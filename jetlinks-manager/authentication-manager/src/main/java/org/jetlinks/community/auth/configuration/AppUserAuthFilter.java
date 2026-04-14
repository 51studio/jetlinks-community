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
package org.jetlinks.community.auth.configuration;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.jetlinks.community.auth.entity.AppUserEntity;
import org.jetlinks.community.auth.service.AppUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;

/**
 * 第三方用户认证过滤器
 * <p>
 * 从请求头 {@code Authorization: Bearer {token}} 中提取 Token，
 * 验证有效后构造 {@link Authentication} 和 {@link AppUserEntity} 注入 ReactorContext。
 * </p>
 *
 * @author jetlinks
 * @since 2.3
 */
@Slf4j
public class AppUserAuthFilter implements WebFilter {

    /** 执行顺序，位于 ApiClientAuthFilter 之后 */
    public static final int ORDER = ApiClientAuthFilter.ORDER + 100;

    private static final String TOKEN_TYPE = "app-user";

    private final AppUserService appUserService;
    private final UserTokenManager userTokenManager;

    public AppUserAuthFilter(AppUserService appUserService, UserTokenManager userTokenManager) {
        this.appUserService = appUserService;
        this.userTokenManager = userTokenManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7).trim();

        return userTokenManager
            .getByToken(token)
            .flatMap(userToken -> {
                // 仅处理 app-user 类型的 Token
                if (!TOKEN_TYPE.equals(userToken.getType())) {
                    return chain.filter(exchange);
                }
                // 校验 Token 状态
                if (!userToken.isNormal()) {
                    return writeError(exchange, HttpStatus.UNAUTHORIZED, "error.app_user_not_found");
                }
                // 刷新 Token 活跃时间
                return userTokenManager
                    .touch(token)
                    .then(appUserService.getById(userToken.getUserId()))
                    .flatMap(appUser -> {
                        if (appUser.getStatus() == null || appUser.getStatus() == 0) {
                            return writeError(exchange, HttpStatus.FORBIDDEN, "error.app_user_disabled");
                        }
                        Authentication auth = buildAuthentication(appUser);
                        return chain
                            .filter(exchange)
                            .contextWrite(ctx -> ctx
                                .put(Authentication.class, auth)
                                .put(AppUserEntity.class, appUser)
                                .put("app-user-token", token));
                    })
                    .switchIfEmpty(Mono.defer(() ->
                        writeError(exchange, HttpStatus.UNAUTHORIZED, "error.app_user_not_found")));
            })
            .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }

    private Authentication buildAuthentication(AppUserEntity appUser) {
        SimpleUser user = new SimpleUser();
        user.setId(appUser.getId());
        user.setName(appUser.getNickname() != null ? appUser.getNickname() : appUser.getUsername());
        user.setUsername(appUser.getUsername());
        user.setUserType(TOKEN_TYPE);

        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(user);
        auth.setPermissions(Collections.emptyList());
        auth.setDimensions(Collections.emptyList());
        return auth;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String errorCode) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        String body = "{\"status\":" + status.value() + ",\"code\":\"" + errorCode + "\",\"message\":\"" + errorCode + "\"}";
        org.springframework.core.io.buffer.DataBuffer buffer = exchange.getResponse()
            .bufferFactory()
            .wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

}
