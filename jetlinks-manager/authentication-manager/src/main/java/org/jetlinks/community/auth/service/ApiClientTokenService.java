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
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.system.authorization.api.entity.UserEntity;
import org.hswebframework.web.system.authorization.api.service.reactive.ReactiveUserService;
import org.jetlinks.community.auth.entity.ApplicationEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * API 客户端 Bearer Token 服务
 * <p>
 * 颁发随机 Token 存入 Redis，用于第三方系统免签名的长期令牌调用。
 * </p>
 *
 * @author jetlinks
 * @since 2.3
 */
@Service
@AllArgsConstructor
public class ApiClientTokenService {

    private static final String TOKEN_TYPE = "api-client";
    private static final long TOKEN_TTL = Duration.ofHours(24).toMillis();

    private final UserTokenManager userTokenManager;
    private final ApplicationService applicationService;
    private final ReactiveUserService userService;

    /**
     * 为指定客户端颁发 Bearer Token（TTL 24h）
     *
     * @param clientId 客户端 ID
     * @return Bearer Token 字符串
     */
    public Mono<String> issueToken(String clientId) {
        String token = IDGenerator.MD5.generate() + IDGenerator.MD5.generate();
        return applicationService.getByClientId(clientId)
            .flatMap(app -> findVirtualUserId(app.getAppId()))
            .flatMap(userId -> userTokenManager
                .signIn(token, TOKEN_TYPE, userId, TOKEN_TTL)
                .map(userToken -> userToken.getToken()));
    }

    /**
     * 撤销指定客户端的全部 Token（通过 clientId scan 删除，适合低频操作）
     * 简化实现：直接删除指定 token key
     *
     * @param token Bearer Token
     */
    public Mono<Void> revokeToken(String token) {
        return userTokenManager.signOutByToken(token);
    }

    /**
     * 根据 clientId 查找有效的 Token
     * 由于 Redis 中存储的是 token -> clientId 的映射，需要通过 scan 查找
     *
     * @param clientId 客户端 ID
     * @return Token（不存在时返回 empty）
     */
    public Mono<String> getTokenByClientId(String clientId) {
        return applicationService.getByClientId(clientId)
            .flatMap(app -> findVirtualUserId(app.getAppId()))
            .flatMap(userId -> userTokenManager
                .getByUserId(userId)
                .filter(userToken -> TOKEN_TYPE.equals(userToken.getType()))
                .map(userToken -> userToken.getToken())
                .next());
    }

    /**
     * 根据 Bearer Token 获取对应的 API 客户端实体
     * <p>
     * Redis 中存储的是 {@code token -> clientId} 映射，先读取 clientId，再查询客户端信息。
     * </p>
     *
     * @param token Bearer Token 字符串
     * @return API 客户端实体，Token 无效或已过期时返回 empty
     */
    public Mono<ApplicationEntity> getClientByToken(String token) {
        return userTokenManager
            .getByToken(token)
            .filter(userToken -> TOKEN_TYPE.equals(userToken.getType()))
            .flatMap(userToken -> userService.findById(userToken.getUserId()))
            .flatMap(user -> applicationService.getByAppId(user.getUsername()));
    }

    /**
     * 根据应用的 appId 查找对应虚拟用户的 ID。
     * 虚拟用户在应用创建时由 ApplicationService.createAppUserAndBind() 自动创建，
     * 其 username = ApplicationEntity.appId。
     */
    private Mono<String> findVirtualUserId(String appId) {
        return userService.findByUsername(appId)
            .map(UserEntity::getId)
            .switchIfEmpty(Mono.error(new BusinessException("error.application.virtual_user_not_found",
                "应用虚拟用户不存在，请确认应用已正确创建")));
    }

    /**
     * 获取或创建 Token
     * 如果存在有效的 Token 则返回，否则创建新的 Token
     *
     * @param clientId 客户端 ID
     * @return Bearer Token 字符串
     */
    public Mono<String> getOrCreateToken(String clientId) {
        return getTokenByClientId(clientId)
            .switchIfEmpty(Mono.defer(() -> issueToken(clientId)));
    }
}