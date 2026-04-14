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
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.community.auth.entity.ApiClientEntity;
import org.jetlinks.community.auth.enums.ApiClientState;
import org.jetlinks.community.auth.web.response.ApiClientKeyResponse;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * API 客户端业务服务
 *
 * @author jetlinks
 * @since 2.3
 */
@Slf4j
@Service
@AllArgsConstructor
public class ApiClientService extends GenericReactiveCrudService<ApiClientEntity, String> {

    private static final String CACHE_KEY_PREFIX = "api_client:info:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ReactiveRedisOperations<Object, Object> redis;

    public Mono<ApiClientEntity> createClient(ApiClientEntity entity) {
        if (!org.springframework.util.StringUtils.hasText(entity.getSecretId())) {
            String raw = IDGenerator.MD5.generate().toUpperCase().replaceAll("[^A-Z0-9]", "");
            entity.setSecretId("AK-" + (raw.length() >= 16 ? raw.substring(0, 16) : raw));
        }
        return this.insert(Mono.just(entity)).thenReturn(entity);
    }

    public Mono<ApiClientKeyResponse> generateKeys(String clientId) {
        String newSecretKey = IDGenerator.MD5.generate() + IDGenerator.MD5.generate();
        return this
            .createUpdate()
            .set(ApiClientEntity::getSecretKey, newSecretKey)
            .where(ApiClientEntity::getId, clientId)
            .execute()
            .flatMap(rows -> {
                if (rows == 0) {
                    return Mono.error(() -> new BusinessException("error.api_client_not_found"));
                }
                return evictCache(clientId)
                    .thenReturn(ApiClientKeyResponse.of(clientId, newSecretKey));
            });
    }

    /**
     * 根据 SecretId（AccessKey）查询客户端，结果缓存 Redis 5 分钟
     *
     * @param secretId AccessKey
     * @return 客户端实体
     */
    public Mono<ApiClientEntity> getBySecretId(String secretId) {
        String cacheKey = CACHE_KEY_PREFIX + "sid:" + secretId;
        return redis
            .opsForValue()
            .get(cacheKey)
            .cast(ApiClientEntity.class)
            .switchIfEmpty(Mono.defer(() -> this
                .createQuery()
                .where(ApiClientEntity::getSecretId, secretId)
                .fetchOne()
                .flatMap(entity -> redis
                    .opsForValue()
                    .set(cacheKey, entity, CACHE_TTL)
                    .thenReturn(entity))
            ));
    }

    /**
     * 根据客户端 ID 获取（含 Redis 缓存）
     *
     * @param clientId 客户端 ID
     * @return 客户端实体
     */
    public Mono<ApiClientEntity> getByClientId(String clientId) {
        String cacheKey = CACHE_KEY_PREFIX + clientId;
        return redis
            .opsForValue()
            .get(cacheKey)
            .cast(ApiClientEntity.class)
            .switchIfEmpty(Mono.defer(() -> this
                .findById(clientId)
                .flatMap(entity -> redis
                    .opsForValue()
                    .set(cacheKey, entity, CACHE_TTL)
                    .thenReturn(entity))
            ));
    }

    /**
     * 启用客户端
     */
    public Mono<Void> enable(String clientId) {
        return this
            .createUpdate()
            .set(ApiClientEntity::getState, ApiClientState.enabled)
            .where(ApiClientEntity::getId, clientId)
            .execute()
            .then(evictCache(clientId));
    }

    /**
     * 禁用客户端
     */
    public Mono<Void> disable(String clientId) {
        return this
            .createUpdate()
            .set(ApiClientEntity::getState, ApiClientState.disabled)
            .where(ApiClientEntity::getId, clientId)
            .execute()
            .then(evictCache(clientId));
    }

    /**
     * 清除指定客户端的 Redis 缓存
     */
    public Mono<Void> evictCache(String clientId) {
        return this
            .findById(clientId)
            .flatMap(entity -> {
                String byId = CACHE_KEY_PREFIX + clientId;
                String bySid = CACHE_KEY_PREFIX + "sid:" + entity.getSecretId();
                return redis.delete(byId, bySid).then();
            })
            .switchIfEmpty(redis.delete(CACHE_KEY_PREFIX + clientId).then());
    }

}
