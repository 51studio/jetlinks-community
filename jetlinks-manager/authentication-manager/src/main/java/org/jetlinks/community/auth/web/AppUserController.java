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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.auth.entity.AppUserEntity;
import org.jetlinks.community.auth.service.ApplicationService;
import org.jetlinks.community.auth.service.ApiClientTokenService;
import org.jetlinks.community.auth.service.AppUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 第三方用户 REST 接口
 * <p>注意：登录、注册接口公开，不需要认证；其他接口通过 {@code AppUserAuthFilter} 保护。</p>
 *
 * @author jetlinks
 * @since 2.3
 */
@RestController
@RequestMapping("/app/user")
@Tag(name = "第三方用户接口")
@AllArgsConstructor
@Slf4j
public class AppUserController {

    private final AppUserService appUserService;
    private final ApplicationService applicationService;
    private final ApiClientTokenService apiClientTokenService;

    // -----------------------------------------------------------------------
    // 公开接口
    // -----------------------------------------------------------------------

    /**
     * 注册
     */
    @PostMapping("/register")
    @Operation(summary = "第三方用户注册")
    public Mono<AppUserEntity> register(@RequestBody AppUserEntity entity) {
        return appUserService.register(entity);
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    @Operation(summary = "第三方用户登录，返回 token 和 userId")
    public Mono<LoginResponse> login(@RequestBody LoginRequest request) {
        return appUserService
            .login(request.getAppId(), request.getUsername(), request.getPassword())
            .map(userToken -> new LoginResponse(userToken.getToken(), userToken.getUserId()));
    }

    // -----------------------------------------------------------------------
    // 需登录接口（从 ReactorContext 获取当前用户）
    // -----------------------------------------------------------------------

    /**
     * 登出
     */
    @PostMapping("/logout")
    @Operation(summary = "第三方用户登出")
    public Mono<Void> logout() {
        return currentAppUser()
            .flatMap(ignored ->
                Mono.deferContextual(ctx ->
                    Mono.justOrEmpty(ctx.getOrEmpty("app-user-token"))
                        .cast(String.class)
                        .flatMap(appUserService::logout)
                )
            );
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前 第三方用户信息")
    public Mono<AppUserEntity> me() {
        return currentAppUser()
            .doOnNext(u -> u.setPassword(null));
    }

    /**
     * 更新当前用户基本信息（昵称/头像/邮箱/手机号）
     */
    @PutMapping("/me")
    @Operation(summary = "更新当前 第三方用户基本信息")
    public Mono<Void> updateMe(@RequestBody UpdateProfileRequest request) {
        return currentAppUser()
            .flatMap(currentUser -> {
                AppUserEntity patch = new AppUserEntity();
                patch.setId(currentUser.getId());
                if (request.getNickname() != null) {
                    patch.setNickname(request.getNickname());
                }
                if (request.getAvatar() != null) {
                    patch.setAvatar(request.getAvatar());
                }
                if (request.getEmail() != null) {
                    patch.setEmail(request.getEmail());
                }
                if (request.getPhone() != null) {
                    patch.setPhone(request.getPhone());
                }
                return appUserService
                    .updateById(currentUser.getId(), Mono.just(patch))
                    .then(appUserService.evictCache(currentUser.getId(), currentUser.getUsername()));
            });
    }

    /**
     * 修改密码
     */
    @PutMapping("/me/password")
    @Operation(summary = "修改 第三方用户密码")
    public Mono<Void> updatePassword(@RequestBody ChangePasswordRequest request) {
        return currentAppUser()
            .flatMap(currentUser ->
                appUserService.updatePassword(
                    currentUser.getId(),
                    request.getOldPassword(),
                    request.getNewPassword()
                )
            );
    }

    /**
     * 获取所属第三方应用的 Token
     * 如果原 Token 已失效，自动生成新的 Token 并返回
     */
    @GetMapping("/api-client/token")
    @Operation(summary = "获取所属第三方应用的 Token", description = "获取当前用户所属第三方应用的 Token，如果原 Token 已失效则自动生成新的")
    public Mono<ApiClientTokenResponse> getApiClientToken() {
        return currentAppUser()
            .flatMap(currentUser ->
                applicationService
                    .getByAppId(currentUser.getAppId())
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "API client not found")))
                    .flatMap(client -> {
                        if (client.getState() != org.jetlinks.community.auth.enums.ApiClientState.enabled) {
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "API client is disabled"));
                        }
                        return apiClientTokenService
                            .getTokenByClientId(client.getId())
                            .flatMap(existingToken -> Mono.just(new ApiClientTokenResponse(client.getId(), existingToken, false)))
                            .switchIfEmpty(
                                apiClientTokenService
                                    .issueToken(client.getId())
                                    .map(newToken -> new ApiClientTokenResponse(client.getId(), newToken, true))
                            )
                            .onErrorResume(err -> {
                                log.error("获取 API Client Token 失败: appId={}, error={}", client.getId(), err.getMessage(), err);
                                return Mono.error(new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR, 
                                    "Failed to get API client token: " + err.getMessage()));
                            });
                    })
            )
            .onErrorResume(err -> {
                log.error("获取 API Client Token 失败: error={}", err.getMessage(), err);
                if (err instanceof ResponseStatusException) {
                    return Mono.error(err);
                }
                return Mono.error(new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to get API client token: " + err.getMessage()));
            });
    }

    // -----------------------------------------------------------------------
    // 工具方法
    // -----------------------------------------------------------------------

    /**
     * 从 ReactorContext 中获取当前 第三方用户，不存在则返回 401
     */
    private Mono<AppUserEntity> currentAppUser() {
        return Mono.deferContextual(ctx ->
            Mono.justOrEmpty(ctx.getOrEmpty(AppUserEntity.class))
                .cast(AppUserEntity.class)
                .switchIfEmpty(Mono.error(
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "error.app_user_not_found")))
        );
    }

    // -----------------------------------------------------------------------
    // 请求/响应 VO
    // -----------------------------------------------------------------------

    @Getter
    @Setter
    public static class LoginRequest {
        private String appId;
        private String username;
        private String password;
    }

    @Getter
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private String userId;
    }

    @Getter
    @Setter
    public static class UpdateProfileRequest {
        private String nickname;
        private String avatar;
        private String email;
        private String phone;
    }

    @Getter
    @Setter
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }

    @Getter
    @AllArgsConstructor
    public static class ApiClientTokenResponse {
        private String appId;
        private String token;
        private boolean isNewToken;
    }

}
