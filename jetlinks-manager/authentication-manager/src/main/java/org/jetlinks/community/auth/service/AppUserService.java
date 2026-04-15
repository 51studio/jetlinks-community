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

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.core.param.TermType;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.token.UserToken;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.community.auth.entity.AppUserEntity;
import org.jetlinks.community.auth.enums.ApiClientState;
import org.jetlinks.community.auth.service.ApplicationService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * 第三方用户业务服务
 *
 * @author jetlinks
 * @since 2.3
 */
@Slf4j
@Service
public class AppUserService extends GenericReactiveCrudService<AppUserEntity, String> {

    private static final String CACHE_PREFIX_BY_ID   = "app_user:id:";
    private static final String CACHE_PREFIX_BY_NAME = "app_user:uname:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** Token 有效期：7 天（毫秒），-1 表示永不过期 */
    private static final long TOKEN_INACTIVE_INTERVAL = Duration.ofDays(7).toMillis();
    private static final String TOKEN_TYPE = "app-user";

    /** 密码分隔符，hash = sha256(salt + ":" + plainPwd) */
    private static final String PWD_SEPARATOR = "$sha256$";

    private final ReactiveRedisOperations<Object, Object> redis;
    private final UserTokenManager userTokenManager;
    private final ApplicationService applicationService;

    public AppUserService(ReactiveRedisOperations<Object, Object> redis,
                          UserTokenManager userTokenManager,
                          ApplicationService applicationService) {
        this.redis = redis;
        this.userTokenManager = userTokenManager;
        this.applicationService = applicationService;
    }

    // -----------------------------------------------------------------------
    // 注册
    // -----------------------------------------------------------------------

    /**
     * 注册新 第三方用户
     * <p>密码 BCrypt 加密后存库；username 唯一性在数据库索引层保障，此处提前校验并返回友好提示。</p>
     *
     * @param entity 用户信息（password 为明文）
     * @return 保存后的实体（password 字段已清空）
     */
    public Mono<AppUserEntity> register(AppUserEntity entity) {
        if (entity.getAppId() == null || entity.getAppId().isBlank()) {
            return Mono.error(new BusinessException("error.api_client_id_required", 400));
        }
        return applicationService
            .getByAppId(entity.getAppId())
            .switchIfEmpty(Mono.error(new BusinessException("error.api_client_not_found", 404)))
            .flatMap(client -> {
                if (client.getState() != ApiClientState.enabled) {
                    return Mono.error(new BusinessException("error.api_client_disabled", 403));
                }
                return this
                    .createQuery()
                    .where(AppUserEntity::getAppId, entity.getAppId())
                    .and(AppUserEntity::getUsername, entity.getUsername())
                    .count()
                    .flatMap(count -> {
                        if (count > 0) {
                            return Mono.error(new BusinessException("error.app_user_username_exists", 400));
                        }
                        // SHA-256 + salt 加密
                        entity.setPassword(encodePassword(entity.getPassword()));
                        if (entity.getStatus() == null) {
                            entity.setStatus((byte) 1);
                        }
                        return this
                            .insert(Mono.just(entity))
                            .thenReturn(entity)
                            .doOnNext(saved -> saved.setPassword(null));
                    });
            });
    }

    // -----------------------------------------------------------------------
    // 登录
    // -----------------------------------------------------------------------

    /**
     * 用户名 + 密码登录，成功后颌发 Bearer Token
     *
     * @param appId 应用 ID
     * @param username 用户名
     * @param password 明文密码
     * @return UserToken（含 token 字符串）
     */
    public Mono<UserToken> login(String appId, String username, String password) {
        return getByUsername(appId, username)
            .switchIfEmpty(Mono.error(new BusinessException("error.app_user_not_found", HttpStatus.NOT_FOUND.value())))
            .flatMap(user -> {
                if (user.getStatus() == null || user.getStatus() == 0) {
                    return Mono.error(new BusinessException("error.app_user_disabled", HttpStatus.FORBIDDEN.value()));
                }
                if (!matchesPassword(password, user.getPassword())) {
                    return Mono.error(new BusinessException("error.app_user_password_not_correct", HttpStatus.UNAUTHORIZED.value()));
                }
                String token = IDGenerator.MD5.generate() + IDGenerator.MD5.generate();
                return userTokenManager.signIn(token, TOKEN_TYPE, user.getId(), TOKEN_INACTIVE_INTERVAL);
            });
    }

    // -----------------------------------------------------------------------
    // 登出
    // -----------------------------------------------------------------------

    /**
     * 销毁指定 Token
     */
    public Mono<Void> logout(String token) {
        return userTokenManager.signOutByToken(token);
    }

    // -----------------------------------------------------------------------
    // 修改密码
    // -----------------------------------------------------------------------

    /**
     * 修改密码
     *
     * @param userId    用户 ID
     * @param oldPwd    旧密码（明文）
     * @param newPwd    新密码（明文）
     */
    public Mono<Void> updatePassword(String userId, String oldPwd, String newPwd) {
        return findById(userId)
            .switchIfEmpty(Mono.error(new BusinessException("error.app_user_not_found", 404)))
            .flatMap(user -> {
                if (!matchesPassword(oldPwd, user.getPassword())) {
                    return Mono.error(new BusinessException("error.app_user_password_not_correct", 401));
                }
                return this
                    .createUpdate()
                    .set(AppUserEntity::getPassword, encodePassword(newPwd))
                    .where(AppUserEntity::getId, userId)
                    .execute()
                    .then(evictCache(userId, user.getUsername()));
            });
    }

    /**
     * 根据用户ID重置密码（不需要旧密码，用于系统重置）
     *
     * @param userId    用户ID
     * @param newPwd    新密码（明文）
     */
    public Mono<Void> resetPasswordById(String userId, String newPwd) {
        return findById(userId)
            .switchIfEmpty(Mono.error(new BusinessException("error.app_user_not_found", 404)))
            .flatMap(user -> this
                .createUpdate()
                .set(AppUserEntity::getPassword, encodePassword(newPwd))
                .where(AppUserEntity::getId, userId)
                .execute()
                .then(evictCache(userId, user.getUsername())));
    }

    /**
     * 根据应用ID和用户名重置密码
     *
     * @param appId     应用ID
     * @param username  用户名
     * @param newPwd    新密码（明文）
     */
    public Mono<Void> resetPassword(String appId, String username, String newPwd) {
        return getByUsername(appId, username)
            .switchIfEmpty(Mono.error(new BusinessException("error.app_user_not_found", 404)))
            .flatMap(user -> this
                .createUpdate()
                .set(AppUserEntity::getPassword, encodePassword(newPwd))
                .where(AppUserEntity::getId, user.getId())
                .execute()
                .then(evictCache(user.getId(), user.getUsername())));
    }

    // -----------------------------------------------------------------------
    // 缓存查询
    // -----------------------------------------------------------------------

    /**
     * 根据 appId + 用户名查询（Redis 缓存 10min）
     */
    public Mono<AppUserEntity> getByUsername(String appId, String username) {
        String key = CACHE_PREFIX_BY_NAME + appId + ":" + username;
        return redis
            .opsForValue()
            .get(key)
            .cast(AppUserEntity.class)
            .switchIfEmpty(Mono.defer(() -> this
                .createQuery()
                .where(AppUserEntity::getAppId, appId)
                .and(AppUserEntity::getUsername, username)
                .fetchOne()
                .flatMap(entity -> redis
                    .opsForValue()
                    .set(key, entity, CACHE_TTL)
                    .thenReturn(entity))
            ));
    }

    /**
     * 按 appId 分页查询 AppUser
     */
    public Mono<PagerResult<AppUserEntity>> queryByAppId(String appId, QueryParamEntity query) {
        query.and("appId", TermType.eq, appId);
        return queryPager(query);
    }

    /**
     * 根据用户 ID 查询（Redis 缓存 10min）
     */
    public Mono<AppUserEntity> getById(String userId) {
        String key = CACHE_PREFIX_BY_ID + userId;
        return redis
            .opsForValue()
            .get(key)
            .cast(AppUserEntity.class)
            .switchIfEmpty(Mono.defer(() -> this
                .findById(userId)
                .flatMap(entity -> redis
                    .opsForValue()
                    .set(key, entity, CACHE_TTL)
                    .thenReturn(entity))
            ));
    }

    // -----------------------------------------------------------------------
    // 密码工具
    // -----------------------------------------------------------------------

    /**
     * 编码密码：格式为 {@code $sha256${salt}${hash}}
     */
    public static String encodePassword(String plainPwd) {
        String salt = UUID.randomUUID().toString().replace("-", "");
        String hash = DigestUtils.sha256Hex(salt + ":" + plainPwd);
        return PWD_SEPARATOR + salt + PWD_SEPARATOR + hash;
    }

    /**
     * 校验密码
     */
    public static boolean matchesPassword(String plainPwd, String encoded) {
        if (encoded == null || !encoded.startsWith(PWD_SEPARATOR)) {
            return false;
        }
        // encoded = "$sha256${salt}$sha256${hash}"
        // remove leading separator -> "{salt}$sha256${hash}"
        String withoutPrefix = encoded.substring(PWD_SEPARATOR.length());
        int sep = withoutPrefix.indexOf(PWD_SEPARATOR);
        if (sep < 0) {
            return false;
        }
        String salt = withoutPrefix.substring(0, sep);
        String expectedHash = withoutPrefix.substring(sep + PWD_SEPARATOR.length());
        String actualHash = DigestUtils.sha256Hex(salt + ":" + plainPwd);
        return actualHash.equals(expectedHash);
    }

    /**
     * 清除 Redis 缓存（通过 userId 自动查找 appId）
     */
    public Mono<Void> evictCache(String userId, String username) {
        return findById(userId)
            .flatMap(user -> evictCache(userId, user.getAppId(), username))
            .switchIfEmpty(redis.delete(CACHE_PREFIX_BY_ID + userId).then());
    }

    /**
     * 清除 Redis 缓存
     */
    public Mono<Void> evictCache(String userId, String clientId, String username) {
        return redis.delete(
            CACHE_PREFIX_BY_ID + userId,
            CACHE_PREFIX_BY_NAME + clientId + ":" + username
        ).then();
    }

}
