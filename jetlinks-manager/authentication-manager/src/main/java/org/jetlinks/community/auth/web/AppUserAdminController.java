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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.annotation.DeleteAction;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceCrudController;
import org.jetlinks.community.auth.entity.AppUserDeviceEntity;
import org.jetlinks.community.auth.entity.AppUserEntity;
import org.jetlinks.community.auth.enums.AppUserDeviceRelationType;
import org.jetlinks.community.auth.service.AppUserDeviceService;
import org.jetlinks.community.auth.service.AppUserService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * C 端用户管理后台接口（管理员专用）
 * <p>需要后台管理员权限（{@code app-user} 资源）。</p>
 *
 * @author jetlinks
 * @since 2.3
 */
@RestController
@RequestMapping("/app/user/admin")
@AllArgsConstructor
@Resource(id = "app-user", name = "C端用户管理", group = "system")
@Tag(name = "C端用户管理（管理员）")
public class AppUserAdminController implements ReactiveServiceCrudController<AppUserEntity, String> {

    private final AppUserService appUserService;
    private final AppUserDeviceService deviceService;

    @Override
    public AppUserService getService() {
        return appUserService;
    }

    // -----------------------------------------------------------------------
    // 启用 / 禁用
    // -----------------------------------------------------------------------

    /**
     * 启用 C 端用户
     */
    @PostMapping("/{id}/_enable")
    @SaveAction
    @Operation(summary = "启用C端用户")
    public Mono<Void> enable(@PathVariable @Parameter(description = "用户ID") String id) {
        return appUserService
            .createUpdate()
            .set(AppUserEntity::getStatus, (byte) 1)
            .where(AppUserEntity::getId, id)
            .execute()
            .then(appUserService.findById(id)
                .flatMap(u -> appUserService.evictCache(u.getId(), u.getUsername())));
    }

    /**
     * 禁用 C 端用户
     */
    @PostMapping("/{id}/_disable")
    @SaveAction
    @Operation(summary = "禁用C端用户")
    public Mono<Void> disable(@PathVariable @Parameter(description = "用户ID") String id) {
        return appUserService
            .createUpdate()
            .set(AppUserEntity::getStatus, (byte) 0)
            .where(AppUserEntity::getId, id)
            .execute()
            .then(appUserService.findById(id)
                .flatMap(u -> appUserService.evictCache(u.getId(), u.getUsername())));
    }

    // -----------------------------------------------------------------------
    // 重置密码（管理员）
    // -----------------------------------------------------------------------

    /**
     * 管理员重置 C 端用户密码
     */
    @PostMapping("/{id}/password/_reset")
    @SaveAction
    @Operation(summary = "管理员重置C端用户密码")
    public Mono<Void> resetPassword(
        @PathVariable @Parameter(description = "用户ID") String id,
        @RequestBody ResetPasswordRequest request) {
        return appUserService
            .findById(id)
            .flatMap(user -> appUserService
                .createUpdate()
                .set(AppUserEntity::getPassword, AppUserService.encodePassword(request.getNewPassword()))
                .where(AppUserEntity::getId, id)
                .execute()
                .then(appUserService.evictCache(id, user.getUsername()))
            );
    }

    // -----------------------------------------------------------------------
    // 设备绑定管理（管理员视角）
    // -----------------------------------------------------------------------

    /**
     * 查询指定用户绑定的所有设备（简单列表）
     */
    @GetMapping("/{userId}/devices")
    @QueryAction
    @Operation(summary = "查询指定C端用户的设备列表")
    public Flux<AppUserDeviceEntity> listDevices(
        @PathVariable @Parameter(description = "用户ID") String userId) {
        return deviceService.getByUserId(userId);
    }

    /**
     * 查询指定用户的设备关联列表（支持条件过滤，不分页）
     */
    @PostMapping("/{userId}/devices/_query/no-paging")
    @QueryAction
    @Operation(summary = "条件查询指定C端用户的设备关联列表（不分页）")
    public Flux<AppUserDeviceEntity> queryDevicesNoPaging(
        @PathVariable @Parameter(description = "用户ID") String userId,
        @RequestBody Mono<QueryParamEntity> query) {
        return query.flatMapMany(q -> {
            q.and("userId", "eq", userId);
            return deviceService.query(q);
        });
    }

    /**
     * 管理员为指定用户绑定/分享设备
     */
    @PostMapping("/{userId}/devices")
    @SaveAction
    @Operation(summary = "管理员为指定C端用户绑定设备")
    public Mono<AppUserDeviceEntity> bindDevice(
        @PathVariable @Parameter(description = "用户ID") String userId,
        @RequestBody AdminBindDeviceRequest request) {
        AppUserDeviceEntity entity = new AppUserDeviceEntity();
        entity.setUserId(userId);
        entity.setDeviceId(request.getDeviceId());
        entity.setDeviceName(request.getDeviceName());
        entity.setProductId(request.getProductId());
        entity.setDescription(request.getDescription());
        entity.setRelationType(request.getRelationType());
        entity.setOperatorId(request.getOperatorId());
        return deviceService.bindDevice(entity);
    }

    /**
     * 管理员解绑指定用户的设备
     */
    @DeleteMapping("/{userId}/devices/{deviceId}")
    @DeleteAction
    @Operation(summary = "管理员解绑C端用户的设备")
    public Mono<Void> unbindDevice(
        @PathVariable @Parameter(description = "用户ID") String userId,
        @PathVariable @Parameter(description = "设备ID") String deviceId) {
        return deviceService.unbindDevice(userId, deviceId);
    }

    // -----------------------------------------------------------------------
    // 请求 VO
    // -----------------------------------------------------------------------

    @Getter
    @Setter
    public static class ResetPasswordRequest {
        private String newPassword;
    }

    @Getter
    @Setter
    public static class AdminBindDeviceRequest {
        @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private String deviceId;
        @Schema(description = "设备名称")
        private String deviceName;
        @Schema(description = "产品ID")
        private String productId;
        @Schema(description = "备注")
        private String description;
        @Schema(description = "关联类型：manage=管理、bind=绑定、share=分享，默认 bind")
        private AppUserDeviceRelationType relationType;
        @Schema(description = "操作人用户ID（bind 时为绑定人，share 时为分享人）")
        private String operatorId;
    }

}
