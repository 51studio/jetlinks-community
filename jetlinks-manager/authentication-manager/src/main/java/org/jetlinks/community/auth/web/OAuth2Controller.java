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
package org.jetlinks.community.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.auth.entity.ApplicationEntity;
import org.jetlinks.community.auth.enums.ApiClientState;
import org.jetlinks.community.auth.service.ApiClientTokenService;
import org.jetlinks.community.auth.service.ApplicationService;
import org.jetlinks.community.auth.web.response.OAuth2TokenResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 标准 OAuth2 认证接口
 * <p>
 * 目前仅支持 client_credentials 模式，用于第三方系统通过 client_id + client_secret 获取 Bearer Token。
 *
 * @author jetlinks
 * @since 2.11
 */
@RestController
@RequestMapping("/oauth2")
@AllArgsConstructor
@Tag(name = "OAuth2 认证")
public class OAuth2Controller {

    private static final long EXPIRES_IN_SECONDS = 24 * 60 * 60L;

    private final ApplicationService applicationService;
    private final ApiClientTokenService apiClientTokenService;

    /**
     * OAuth2 Token 端点（支持 GET/POST，兼容文档示例与标准规范）
     */
    @RequestMapping(value = "/token", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "OAuth2 获取 Token")
    public Mono<OAuth2TokenResponse> token(
        @RequestParam("grant_type") String grantType,
        @RequestParam("client_id") String clientId,
        @RequestParam("client_secret") String clientSecret) {

        if (!"client_credentials".equals(grantType)) {
            return Mono.error(new BusinessException("error.oauth2.unsupported_grant_type", 400));
        }

        return applicationService
            .getByAppId(clientId)
            .switchIfEmpty(Mono.error(new BusinessException("error.oauth2.invalid_client", 401)))
            .flatMap(app -> {
                if (!clientSecret.equals(app.getSecretKey())) {
                    return Mono.error(new BusinessException("error.oauth2.invalid_client", 401));
                }
                if (app.getState() != ApiClientState.enabled) {
                    return Mono.error(new BusinessException("error.oauth2.client_disabled", 403));
                }
                if (app.getApiServer() == null || !Boolean.TRUE.equals(app.getApiServer().getEnableOAuth2())) {
                    return Mono.error(new BusinessException("error.oauth2.not_enabled", 403));
                }
                return apiClientTokenService
                    .getOrCreateToken(app.getId())
                    .map(token -> OAuth2TokenResponse.of(token, "Bearer", EXPIRES_IN_SECONDS));
            });
    }
}
