/*
 * Copyright 2025 JetLinks https://www.jetlinks.cn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetlinks.community.auth.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.core.param.TermType;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.system.authorization.api.entity.DimensionUserEntity;
import org.hswebframework.web.system.authorization.api.entity.UserEntity;
import org.hswebframework.web.system.authorization.api.service.reactive.ReactiveUserService;
import org.hswebframework.web.system.authorization.defaults.service.DefaultDimensionUserService;
import org.jetlinks.community.auth.entity.*;
import org.jetlinks.community.auth.enums.ApiClientState;
import org.jetlinks.community.auth.enums.ApplicationProvider;
import org.jetlinks.community.auth.enums.DefaultUserEntityType;
import org.jetlinks.community.auth.web.ApplicationController;
import org.jetlinks.community.auth.web.request.ApplicationSaveRequest;
import org.jetlinks.community.auth.web.response.ApiClientKeyResponse;
import org.jetlinks.community.authorize.OrgDimensionType;
import org.jetlinks.community.config.ConfigManager;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用管理服务
 * <p>
 * 替代原有的 ApiClientService，完整支持前端应用管理表单数据结构。
 *
 * @author jetlinks
 * @since 2.11
 */
@Slf4j
@Service
@AllArgsConstructor
public class ApplicationService extends GenericReactiveCrudService<ApplicationEntity, String> {

    private static final String CACHE_KEY_PREFIX = "application:info:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String OPERATIONS_SCOPE = "application-operations";

    private final ReactiveRedisOperations<Object, Object> redis;
    private final ConfigManager configManager;
    private final ReactiveUserService userService;
    private final RoleService roleService;
    private final OrganizationService organizationService;
    private final DefaultDimensionUserService dimensionUserService;

    // ----------------------------- 新增/修改/查询 -----------------------------

    public Mono<ApplicationEntity> createApplication(ApplicationSaveRequest request) {
        ApplicationEntity entity = convertRequestToEntity(request);
        return this.insert(Mono.just(entity))
            .thenReturn(entity)
            .flatMap(app -> {
                if ("third-party".equals(app.getProvider())) {
                    return createAppUserAndBind(app).thenReturn(app);
                }
                return Mono.just(app);
            });
    }

    public Mono<Integer> updateApplication(String id, ApplicationSaveRequest request) {
        ApplicationEntity entity = convertRequestToEntity(request);
        entity.setId(id);
        return this.updateById(id, entity)
            .flatMap(rows -> {
                if (rows == 0) {
                    return Mono.error(() -> new BusinessException("error.application_not_found"));
                }
                return evictCache(id)
                    .then(findById(id))
                    .flatMap(app -> {
                        if ("third-party".equals(app.getProvider())) {
                            // 简化处理：重新同步用户密码、角色和组织
                            return syncAppUser(app).thenReturn(rows);
                        }
                        return Mono.just(rows);
                    });
            });
    }

    /**
     * 查询应用详情，并动态注入 apiServer.appId 和 apiServer.secureKey
     */
    public Mono<ApplicationEntity> findDetailById(String id) {
        return findById(id)
            .doOnNext(entity -> {
                if (entity.getApiServer() == null) {
                    entity.setApiServer(new ApplicationApiServerConfig());
                }
                entity.getApiServer().setAppId(entity.getId());
                entity.getApiServer().setSecureKey(entity.getSecretKey());
            });
    }

    // ----------------------------- 密钥与状态 -----------------------------

    public Mono<ApiClientKeyResponse> generateKeys(String clientId) {
        String newSecretKey = IDGenerator.MD5.generate() + IDGenerator.MD5.generate();
        return this
            .createUpdate()
            .set(ApplicationEntity::getSecretKey, newSecretKey)
            .where(ApplicationEntity::getId, clientId)
            .execute()
            .flatMap(rows -> {
                if (rows == 0) {
                    return Mono.error(() -> new BusinessException("error.application_not_found"));
                }
                return evictCache(clientId)
                    .then(findById(clientId))
                    .flatMap(client -> {
                        if ("third-party".equals(client.getProvider())) {
                            return userService
                                .findByUsername(client.getAppId())
                                .flatMap(user -> {
                                    user.setPassword(newSecretKey);
                                    return userService.saveUser(Mono.just(user));
                                })
                                .thenReturn(ApiClientKeyResponse.of(clientId, newSecretKey));
                        }
                        return Mono.just(ApiClientKeyResponse.of(clientId, newSecretKey));
                    });
            });
    }

    public Mono<Void> enable(String clientId) {
        return this
            .createUpdate()
            .set(ApplicationEntity::getState, ApiClientState.enabled)
            .where(ApplicationEntity::getId, clientId)
            .execute()
            .then(evictCache(clientId));
    }

    public Mono<Void> disable(String clientId) {
        return this
            .createUpdate()
            .set(ApplicationEntity::getState, ApiClientState.disabled)
            .where(ApplicationEntity::getId, clientId)
            .execute()
            .then(evictCache(clientId));
    }

    // ----------------------------- 缓存 -----------------------------

    public Mono<ApplicationEntity> getByAppId(String appId) {
        String cacheKey = CACHE_KEY_PREFIX + "aid:" + appId;
        return redis
            .opsForValue()
            .get(cacheKey)
            .cast(ApplicationEntity.class)
            .switchIfEmpty(Mono.defer(() -> this
                .createQuery()
                .where(ApplicationEntity::getAppId, appId)
                .fetchOne()
                .flatMap(entity -> redis
                    .opsForValue()
                    .set(cacheKey, entity, CACHE_TTL)
                    .thenReturn(entity))
            ));
    }

    public Mono<ApplicationEntity> getByClientId(String clientId) {
        String cacheKey = CACHE_KEY_PREFIX + clientId;
        return redis
            .opsForValue()
            .get(cacheKey)
            .cast(ApplicationEntity.class)
            .switchIfEmpty(Mono.defer(() -> this
                .findById(clientId)
                .flatMap(entity -> redis
                    .opsForValue()
                    .set(cacheKey, entity, CACHE_TTL)
                    .thenReturn(entity))
            ));
    }

    public Mono<Void> evictCache(String clientId) {
        return this
            .findById(clientId)
            .flatMap(entity -> {
                String byId = CACHE_KEY_PREFIX + clientId;
                String bySid = CACHE_KEY_PREFIX + "aid:" + entity.getAppId();
                return redis.delete(byId, bySid).then();
            })
            .switchIfEmpty(redis.delete(CACHE_KEY_PREFIX + clientId).then());
    }

    // ----------------------------- API授权 -----------------------------

    public Mono<List<PermissionInfo>> getOperations() {
        return configManager
            .getProperties(OPERATIONS_SCOPE)
            .map(props -> {
                Object operations = props.get("operations");
                List<PermissionInfo> result = new ArrayList<>();
                if (operations instanceof List) {
                    List<?> list = (List<?>) operations;
                    for (Object item : list) {
                        if (item instanceof String) {
                            PermissionInfo info = new PermissionInfo();
                            info.setPermission((String) item);
                            result.add(info);
                        }
                    }
                }
                return result;
            })
            .defaultIfEmpty(new ArrayList<>());
    }

    public Mono<Void> addOperations(ApplicationController.BatchOperationsRequest request) {
        List<String> operations = request.getOperations();
        if (CollectionUtils.isEmpty(operations)) {
            return Mono.empty();
        }
        return configManager
            .getProperties(OPERATIONS_SCOPE)
            .flatMap(props -> {
                Object existing = props.get("operations");
                Set<String> operationSet = new HashSet<>();
                if (existing instanceof List) {
                    ((List<?>) existing).forEach(item -> {
                        if (item instanceof String) {
                            operationSet.add((String) item);
                        }
                    });
                }
                operationSet.addAll(operations);
                Map<String, Object> newProps = new HashMap<>(props.values());
                newProps.put("operations", new ArrayList<>(operationSet));
                return configManager.setProperties(OPERATIONS_SCOPE, newProps);
            })
            .then();
    }

    public Mono<Void> deleteOperations(ApplicationController.BatchOperationsRequest request) {
        List<String> operations = request.getOperations();
        if (CollectionUtils.isEmpty(operations)) {
            return Mono.empty();
        }
        return configManager
            .getProperties(OPERATIONS_SCOPE)
            .flatMap(props -> {
                Object existing = props.get("operations");
                if (!(existing instanceof List)) {
                    return Mono.empty();
                }
                List<?> list = (List<?>) existing;
                List<Object> remaining = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String && !operations.contains(item)) {
                        remaining.add(item);
                    }
                }
                Map<String, Object> newProps = new HashMap<>(props.values());
                newProps.put("operations", remaining);
                return configManager.setProperties(OPERATIONS_SCOPE, newProps);
            })
            .then();
    }

    public Mono<List<String>> getGrantedApis(String clientId) {
        return findById(clientId)
            .map(client -> {
                List<PermissionInfo> permissions = client.getPermissions();
                if (permissions == null) {
                    return Collections.<String>emptyList();
                }
                return permissions
                    .stream()
                    .map(PermissionInfo::getPermission)
                    .collect(Collectors.toList());
            })
            .defaultIfEmpty(Collections.emptyList());
    }

    public Mono<Void> grantApis(String clientId, List<ApplicationController.GrantOperation> operations) {
        final List<ApplicationController.GrantOperation> finalOperations =
            operations != null ? operations : Collections.emptyList();
        return findById(clientId)
            .flatMap(client -> {
                List<PermissionInfo> permissionInfos = finalOperations.stream()
                    .map(op -> {
                        PermissionInfo info = new PermissionInfo();
                        info.setPermission(op.getId());
                        info.setActions(op.getPermissions() != null ? new HashSet<>(op.getPermissions()) : null);
                        return info;
                    })
                    .collect(Collectors.toList());
                client.setPermissions(permissionInfos);
                return updateById(clientId, client);
            })
            .then();
    }

    // ----------------------------- 角色与组织 -----------------------------

    public Mono<Void> bindRole(String clientId, List<String> roleIdList) {
        return roleService.bindUser(Collections.singleton(clientId), roleIdList, false);
    }

    public Mono<Void> unbindRole(String clientId, List<String> roleIdList) {
        return roleService.unbindUser(Collections.singleton(clientId), roleIdList);
    }

    public Mono<org.hswebframework.web.api.crud.entity.PagerResult<DimensionUserEntity>> queryBoundRoles(
        String clientId,
        org.hswebframework.web.api.crud.entity.QueryParamEntity query) {
        query.and("userId", TermType.eq, clientId);
        query.and("dimensionTypeId", TermType.eq, org.hswebframework.web.authorization.DefaultDimensionType.role.getId());
        return dimensionUserService.queryPager(query);
    }

    public Mono<Void> bindOrg(String clientId, List<String> orgIds) {
        return organizationService.bindUser(
            Collections.singleton(clientId),
            orgIds != null ? orgIds : Collections.emptyList(),
            false)
            .then();
    }

    public Mono<Void> unbindOrg(String clientId, List<String> orgIds) {
        List<String> ids = orgIds != null ? orgIds : Collections.emptyList();
        if (ids.isEmpty()) {
            return Mono.empty();
        }
        return dimensionUserService.createDelete()
            .where(DimensionUserEntity::getUserId, clientId)
            .and(DimensionUserEntity::getDimensionTypeId, OrgDimensionType.org.getId())
            .in(DimensionUserEntity::getDimensionId, ids)
            .execute()
            .then();
    }

    public Mono<org.hswebframework.web.api.crud.entity.PagerResult<DimensionUserEntity>> queryBoundOrgs(
        String clientId,
        org.hswebframework.web.api.crud.entity.QueryParamEntity query) {
        query.and("userId", TermType.eq, clientId);
        query.and("dimensionTypeId", TermType.eq, OrgDimensionType.org.getId());
        return dimensionUserService.queryPager(query);
    }

    // ----------------------------- 私有方法 -----------------------------

    private ApplicationEntity convertRequestToEntity(ApplicationSaveRequest req) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(req.getId());
        entity.setName(req.getName());
        entity.setProvider(req.getProvider());
        entity.setLogoUrl(req.getLogoUrl());
        entity.setDescription(req.getDescription());

        if (!CollectionUtils.isEmpty(req.getIntegrationModes())) {
            entity.setIntegrationModes(
                req.getIntegrationModes().stream()
                    .map(v -> new IntegrationMode(v, ApplicationProvider.getModeText(v)))
                    .collect(Collectors.toList())
            );
        }

        // 提取 apiServer 敏感字段到主表，避免重复存储
        if (req.getApiServer() != null) {
            entity.setSecretKey(req.getApiServer().getSecureKey());
            entity.setIpWhiteList(req.getApiServer().getIpWhiteList());
            if (req.getApiServer().getEnableOAuth2() != null) {
                // rateLimit 等字段也可以在此扩展
            }
            ApplicationApiServerConfig server = new ApplicationApiServerConfig();
            BeanUtils.copyProperties(req.getApiServer(), server);
            server.setAppId(null);
            server.setSecureKey(null);
            entity.setApiServer(server);
        }

        entity.setPage(req.getPage());

        if (req.getApiClient() != null) {
            ApplicationApiClientConfig client = new ApplicationApiClientConfig();
            BeanUtils.copyProperties(req.getApiClient(), client);
            entity.setApiClient(client);
        }

        entity.setSso(req.getSso());

        if (!StringUtils.hasText(entity.getAppId())) {
            entity.setAppId(generateAppId());
        }

        if (entity.getState() == null) {
            entity.setState(ApiClientState.enabled);
        }

        return entity;
    }

    private String generateAppId() {
        String raw = IDGenerator.MD5.generate().toUpperCase().replaceAll("[^A-Z0-9]", "");
        return "AK-" + (raw.length() >= 16 ? raw.substring(0, 16) : raw);
    }

    /**
     * 为第三方应用创建用户并绑定角色和组织
     */
    private Mono<ApplicationEntity> createAppUserAndBind(ApplicationEntity app) {
        UserEntity user = new UserEntity();
        user.setId(IDGenerator.SNOW_FLAKE.generate().toString());
        user.setUsername(app.getAppId());
        user.setName(app.getName());
        String secretKey = app.getSecretKey();
        user.setPassword(secretKey != null ? secretKey : app.getAppId());
        user.setStatus((byte) 1);
        user.setType(DefaultUserEntityType.APPLICATION.getId());

        return userService.saveUser(Mono.just(user))
            .then(userService.findById(user.getId()))
            .flatMap(savedUser -> syncUserBindings(savedUser.getId(), app))
            .thenReturn(app)
            .onErrorResume(e -> {
                log.warn("为第三方应用创建用户失败: {}", app.getId(), e);
                return Mono.just(app);
            });
    }

    /**
     * 同步第三方应用的用户绑定信息
     */
    private Mono<Void> syncAppUser(ApplicationEntity app) {
        return userService.findByUsername(app.getAppId())
            .flatMap(user -> syncUserBindings(user.getId(), app))
            .switchIfEmpty(createAppUserAndBind(app).then());
    }

    private Mono<Void> syncUserBindings(String userId, ApplicationEntity app) {
        List<String> roleIds = extractRoleIds(app);
        List<String> orgIds = extractOrgIds(app);

        Mono<Void> bindRoles = Mono.empty();
        Mono<Void> bindOrgs = Mono.empty();

        if (!CollectionUtils.isEmpty(roleIds)) {
            bindRoles = roleService.bindUser(Collections.singletonList(userId), roleIds, false);
        }
        if (!CollectionUtils.isEmpty(orgIds)) {
            bindOrgs = organizationService.bindUser(Collections.singletonList(userId), orgIds, false);
        }

        return Mono.when(bindRoles, bindOrgs).then();
    }

    private List<String> extractRoleIds(ApplicationEntity app) {
        List<String> result = new ArrayList<>();
        if (app.getApiServer() != null && !CollectionUtils.isEmpty(app.getApiServer().getRoleIdList())) {
            result.addAll(app.getApiServer().getRoleIdList());
        }
        if (app.getSso() != null && !CollectionUtils.isEmpty(app.getSso().getRoleIdList())) {
            result.addAll(app.getSso().getRoleIdList());
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    private List<String> extractOrgIds(ApplicationEntity app) {
        List<String> result = new ArrayList<>();
        if (app.getApiServer() != null && !CollectionUtils.isEmpty(app.getApiServer().getOrgIdList())) {
            result.addAll(app.getApiServer().getOrgIdList());
        }
        if (app.getSso() != null && !CollectionUtils.isEmpty(app.getSso().getOrgIdList())) {
            result.addAll(app.getSso().getOrgIdList());
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

}
