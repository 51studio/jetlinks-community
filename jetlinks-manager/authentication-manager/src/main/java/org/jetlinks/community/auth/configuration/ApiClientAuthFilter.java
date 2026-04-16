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
import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimplePermission;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.jetlinks.community.auth.entity.ApplicationEntity;
import org.jetlinks.community.auth.entity.PermissionInfo;
import org.jetlinks.community.auth.enums.ApiClientState;
import org.jetlinks.community.auth.service.ApiClientAccessLogService;
import org.jetlinks.community.auth.service.ApiClientRateLimiter;
import org.jetlinks.community.auth.service.ApiClientTokenService;
import org.jetlinks.community.auth.service.ApplicationService;
import org.jetlinks.community.web.permission.ApiOperationPermission;
import org.jetlinks.community.web.permission.ApiOperationPermissionMappingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API 客户端认证过滤器
 * <p>
 * 支持两种认证模式：
 * <ol>
 *   <li><b>Access Token</b>：请求头 {@code X-Access-Token: {token}}，Token 由
 *       {@link ApiClientTokenService#issueToken(String)} 颁发，TTL 24h。</li>
 *   <li><b>签名模式</b>：请求头 {@code X-Client-Id: {appId}}、
 *       {@code X-Client-Sign: {sign}}、{@code X-Timestamp: {epochMs}}，
 *       签名 = {@code HMAC-SHA256(secretKey, "{appId}:{timestamp}")}。</li>
 * </ol>
 * 认证成功后将构造 {@link Authentication} 注入 ReactorContext，后续接口透明使用。
 * </p>
 *
 * @author jetlinks
 * @since 2.3
 */
@Slf4j
public class ApiClientAuthFilter implements WebFilter {

    /**
     * 过滤器执行顺序，位于 TraceWebFilter（HIGHEST_PRECEDENCE+100）之后
     */
    public static final int ORDER = Integer.MIN_VALUE + 200;

    /**
     * 签名有效期（毫秒），防重放
     */
    private static final long SIGN_VALID_MILLIS = 5 * 60 * 1000L;

    private final ApplicationService applicationService;
    private final ApiClientRateLimiter rateLimiter;
    private final ApiClientAccessLogService accessLogService;
    private final ReactiveAuthenticationManager authenticationManager;
    private final UserTokenManager userTokenManager;
    private final ApiOperationPermissionMappingService operationMappingService;

    public ApiClientAuthFilter(ApplicationService applicationService,
                               ApiClientRateLimiter rateLimiter,
                               ApiClientAccessLogService accessLogService,
                               ReactiveAuthenticationManager authenticationManager,
                               UserTokenManager userTokenManager,
                               ApiOperationPermissionMappingService operationMappingService) {
        this.applicationService = applicationService;
        this.rateLimiter = rateLimiter;
        this.accessLogService = accessLogService;
        this.authenticationManager = authenticationManager;
        this.userTokenManager = userTokenManager;
        this.operationMappingService = operationMappingService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();

        // 尝试 Access Token 模式
        String accessToken = headers.getFirst("X-Access-Token");
        if (StringUtils.hasText(accessToken)) {
            return handleAccessToken(accessToken, exchange, chain);
        }

        // 尝试签名模式
        String clientId = headers.getFirst("X-Client-Id");
        if (StringUtils.hasText(clientId)) {
            String sign = headers.getFirst("X-Client-Sign");
            String timestamp = headers.getFirst("X-Timestamp");
            return handleSignature(clientId, sign, timestamp, exchange, chain);
        }

        // 不是 API 客户端请求，透传给原有认证链
        return chain.filter(exchange);
    }

    // ----------------------------- Access Token 模式 -----------------------------

    private Mono<Void> handleAccessToken(String token,
                                         ServerWebExchange exchange,
                                         WebFilterChain chain) {
        return userTokenManager
            .getByToken(token)
            .flatMap(userToken -> {
                if (!"api-client".equals(userToken.getType())) {
                    return chain.filter(exchange);
                }
                return userTokenManager
                    .touch(userToken.getToken())
                    .then(applicationService.getByClientId(userToken.getUserId()))
                    .flatMap(client -> authenticate(client, exchange, chain));
            })
            .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }

    // ----------------------------- 签名模式 -----------------------------

    private Mono<Void> handleSignature(String appId,
                                       String sign,
                                       String timestamp,
                                       ServerWebExchange exchange,
                                       WebFilterChain chain) {
        if (!StringUtils.hasText(sign) || !StringUtils.hasText(timestamp)) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "error.api_client_sign_invalid");
        }

        // 验证时间戳防重放
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "error.api_client_sign_invalid");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > SIGN_VALID_MILLIS) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "error.api_client_sign_invalid");
        }

        return applicationService
            .getByAppId(appId)
            .flatMap(client -> {
                // 验证签名
                String expected = hmacSha256(client.getSecretKey(), appId + ":" + timestamp);
                if (!expected.equals(sign)) {
                    return writeError(exchange, HttpStatus.UNAUTHORIZED, "error.api_client_sign_invalid");
                }
                return authenticate(client, exchange, chain);
            })
            .switchIfEmpty(writeError(exchange, HttpStatus.UNAUTHORIZED, "error.api_client_sign_invalid"));
    }

    private Mono<Void> authenticate(ApplicationEntity client,
                                    ServerWebExchange exchange,
                                    WebFilterChain chain) {
        if (client.getState() != ApiClientState.enabled) {
            return writeError(exchange, HttpStatus.FORBIDDEN, "error.api_client_disabled");
        }

        if (StringUtils.hasText(client.getIpWhiteList())) {
            String remoteIp = getClientIp(exchange.getRequest());
            boolean allowed = false;
            for (String ip : client.getIpWhiteList().split(",")) {
                if (ip.trim().equals(remoteIp)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return writeError(exchange, HttpStatus.FORBIDDEN, "error.api_client_ip_not_allowed");
            }
        }

        int rateLimit = client.getRateLimit() == null ? 0 : client.getRateLimit();
        String ip = getClientIp(exchange.getRequest());
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        Mono<Authentication> authMono;
        authMono = authenticationManager
            .getByUserId(client.getId())
            .map(auth -> {
                SimpleUser user = new SimpleUser();
                user.setId(client.getId());
                user.setName(client.getName());
                user.setUsername(client.getAppId());
                user.setUserType("api-client");
                if (auth instanceof SimpleAuthentication) {
                    ((SimpleAuthentication) auth).setUser(user);
                }
                return auth;
            })
            .switchIfEmpty(Mono.fromSupplier(() -> buildAuthentication(client)))
            .map(auth -> mergeClientPermissions(auth, client));

        return authMono.flatMap(auth ->
            rateLimiter
                .checkAndIncrement(client.getId(), rateLimit)
                .then(chain
                    .filter(exchange)
                    .contextWrite(ctx -> ctx.put(Authentication.class, auth))
                    .doFinally(signal -> {
                        int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 200;
                        accessLogService.asyncRecord(
                            client.getId(), client.getName(), path, method, ip, status);
                    }))
                .onErrorResume(e -> {
                    if (e instanceof org.hswebframework.web.exception.BusinessException) {
                        String code = e.getMessage();
                        if ("error.api_client_rate_limit_exceeded".equals(code)) {
                            return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, code);
                        }
                    }
                    return Mono.error(e);
                })
        );
    }

    /**
     * 根据 API 客户端配置构建 Authentication 对象
     */
    private Authentication buildAuthentication(ApplicationEntity client) {
        SimpleUser user = new SimpleUser();
        user.setId(client.getId());
        user.setName(client.getName());
        user.setUsername(client.getAppId());
        user.setUserType("api-client");

        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(user);

        List<PermissionInfo> permissions = client.getPermissions();
        if (!CollectionUtils.isEmpty(permissions)) {
            List<org.hswebframework.web.authorization.Permission> permList = permissions
                .stream()
                .map(p -> SimplePermission
                    .builder()
                    .id(p.getPermission())
                    .name(p.getName() != null ? p.getName() : p.getPermission())
                    .actions(p.getActions() != null ? p.getActions() : Collections.emptySet())
                    .build())
                .collect(Collectors.toList());
            auth.setPermissions(permList);
        } else {
            auth.setPermissions(Collections.emptyList());
        }
        auth.setDimensions(Collections.emptyList());

        return auth;
    }

    /**
     * 将应用管理中赋权的权限合并到 Authentication 中。
     * <p>
     * 数据库中可能保存的是 Swagger operationId（如 getById_1），
     * 需要优先通过 {@link ApiOperationPermissionMappingService} 映射为 hswebframework 的
     * resourceId + actions，再与现有权限合并。
     */
    private Authentication mergeClientPermissions(Authentication auth, ApplicationEntity client) {
        List<PermissionInfo> clientPermissions = client.getPermissions();
        if (CollectionUtils.isEmpty(clientPermissions)) {
            return auth;
        }

        List<org.hswebframework.web.authorization.Permission> existingPerms = auth.getPermissions();
        if (existingPerms == null) {
            existingPerms = new ArrayList<>();
        } else {
            existingPerms = new ArrayList<>(existingPerms);
        }

        Map<String, org.hswebframework.web.authorization.Permission> permMap = existingPerms
            .stream()
            .collect(Collectors.toMap(org.hswebframework.web.authorization.Permission::getId, p -> p));

        for (PermissionInfo p : clientPermissions) {
            // 尝试将 operationId 映射为 resourceId + actions
            ApiOperationPermission mapped = operationMappingService
                .resolve(p.getPermission())
                .orElse(null);

            String permId;
            Set<String> actions = new HashSet<>();
            if (mapped != null) {
                permId = mapped.getResourceId();
                actions.addAll(mapped.getActions());
            } else {
                permId = p.getPermission();
                if (p.getActions() != null) {
                    actions.addAll(p.getActions());
                }
            }

            org.hswebframework.web.authorization.Permission existing = permMap.get(permId);
            if (existing != null) {
                Set<String> mergedActions = new HashSet<>(existing.getActions());
                mergedActions.addAll(actions);
                permMap.put(permId, SimplePermission
                    .builder()
                    .id(existing.getId())
                    .name(existing.getName())
                    .actions(mergedActions)
                    .build());
            } else {
                permMap.put(permId, SimplePermission
                    .builder()
                    .id(permId)
                    .name(p.getName() != null ? p.getName() : permId)
                    .actions(actions)
                    .build());
            }
        }

        if (auth instanceof SimpleAuthentication) {
            ((SimpleAuthentication) auth).setPermissions(new ArrayList<>(permMap.values()));
        }

        return auth;
    }

    /**
     * HMAC-SHA256 签名
     */
    private String hmacSha256(String key, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey =
                new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] bytes = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 error", e);
        }
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        return request.getRemoteAddress() != null
            ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * 返回 JSON 格式的错误响应
     */
    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String errorCode) {
        if (exchange.getResponse().isCommitted()) {
            log.warn("Response already committed, skip writing error: {}", errorCode);
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        String body = "{\"status\":" + status.value() + ",\"code\":\"" + errorCode + "\",\"message\":\"" + errorCode + "\"}";
        org.springframework.core.io.buffer.DataBuffer buffer = exchange.getResponse()
            .bufferFactory()
            .wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

}
