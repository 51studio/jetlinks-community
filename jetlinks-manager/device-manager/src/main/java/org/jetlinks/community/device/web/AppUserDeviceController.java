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
package org.jetlinks.community.device.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.community.auth.entity.AppUserDeviceEntity;
import org.jetlinks.community.auth.entity.AppUserEntity;
import org.jetlinks.community.auth.enums.AppUserDeviceRelationType;
import org.jetlinks.community.auth.service.AppUserDeviceService;
import org.jetlinks.community.device.enums.DeviceState;
import org.jetlinks.community.device.service.LocalDeviceInstanceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * C 端用户设备绑定 REST 接口
 * <p>所有端点均需 C 端用户登录（由 {@code AppUserAuthFilter} 拦截并注入 {@link AppUserEntity}）。</p>
 *
 * @author jetlinks
 * @since 2.3
 */
@RestController
@RequestMapping("/app/user/device")
@Tag(name = "C端用户设备绑定接口")
@AllArgsConstructor
public class AppUserDeviceController {

    private final AppUserDeviceService deviceService;
    private final LocalDeviceInstanceService deviceInstanceService;

    // -----------------------------------------------------------------------
    // 查询
    // -----------------------------------------------------------------------

    /**
     * 查询当前用户绑定的所有设备（包含设备状态）
     */
    @GetMapping
    @Operation(summary = "查询当前 C 端用户绑定的设备列表")
    public Flux<DeviceBindingInfo> listDevices() {
        return currentAppUser()
            .flatMapMany(user -> deviceService.getByUserId(user.getId())
                .flatMap(binding -> deviceInstanceService
                    .getDeviceState(binding.getDeviceId())
                    .map(state -> new DeviceBindingInfo(binding, state))
                    .defaultIfEmpty(new DeviceBindingInfo(binding, DeviceState.notActive))
                )
            );
    }

    /**
     * 获取指定设备详情（校验归属，包含设备状态）
     */
    @GetMapping("/{deviceId}")
    @Operation(summary = "获取指定设备的绑定详情（校验归属）")
    public Mono<DeviceBindingInfo> getDevice(@PathVariable String deviceId) {
        return currentAppUser()
            .flatMap(user -> deviceService.assertOwnership(user.getId(), deviceId)
                .flatMap(binding -> deviceInstanceService
                    .getDeviceState(binding.getDeviceId())
                    .map(state -> new DeviceBindingInfo(binding, state))
                    .defaultIfEmpty(new DeviceBindingInfo(binding, DeviceState.notActive))
                )
            );
    }

    // -----------------------------------------------------------------------
    // 绑定
    // -----------------------------------------------------------------------

    /**
     * 绑定设备
     */
    @PostMapping("/bind")
    @Operation(summary = "绑定设备到当前 C 端用户")
    public Mono<AppUserDeviceEntity> bindDevice(@RequestBody BindDeviceRequest request) {
        return currentAppUser()
            .flatMap(user -> {
                AppUserDeviceEntity entity = new AppUserDeviceEntity();
                entity.setUserId(user.getId());
                entity.setDeviceId(request.getDeviceId());
                entity.setDeviceName(request.getDeviceName());
                entity.setProductId(request.getProductId());
                entity.setDescription(request.getDescription());
                entity.setRelationType(request.getRelationType());
                entity.setOperatorId(request.getOperatorId());
                return deviceService.bindDevice(entity);
            });
    }

    // -----------------------------------------------------------------------
    // 修改
    // -----------------------------------------------------------------------

    /**
     * 修改绑定设备的备注和/或关联类型（校验归属）
     */
    @PutMapping("/{deviceId}")
    @Operation(summary = "修改绑定设备的备注和关联类型（校验归属）")
    public Mono<Void> updateDevice(@PathVariable String deviceId,
                                   @RequestBody UpdateDeviceRequest request) {
        return currentAppUser()
            .flatMap(user -> deviceService.updateBinding(
                user.getId(), deviceId,
                request.getDescription(),
                request.getRelationType(),
                request.getOperatorId()
            ));
    }

    // -----------------------------------------------------------------------
    // 解绑
    // -----------------------------------------------------------------------

    /**
     * 解绑设备
     */
    @DeleteMapping("/{deviceId}")
    @Operation(summary = "解绑设备（校验归属）")
    public Mono<Void> unbindDevice(@PathVariable String deviceId) {
        return currentAppUser()
            .flatMap(user -> deviceService.unbindDevice(user.getId(), deviceId));
    }

    // -----------------------------------------------------------------------
    // 工具方法
    // -----------------------------------------------------------------------

    /**
     * 从 ReactorContext 获取当前 C 端用户，不存在则返回 401
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
    // 请求 VO
    // -----------------------------------------------------------------------

    @Getter
    @Setter
    public static class BindDeviceRequest {
        private String deviceId;
        private String deviceName;
        private String productId;
        private String description;
        @Schema(description = "关联类型：manage=管理、bind=绑定、share=分享，默认 bind")
        private AppUserDeviceRelationType relationType;
        @Schema(description = "操作人用户ID（bind 时为绑定人，share 时为分享人）")
        private String operatorId;
    }

    @Getter
    @Setter
    public static class UpdateDeviceRequest {
        private String description;
        @Schema(description = "关联类型（可选）")
        private AppUserDeviceRelationType relationType;
        @Schema(description = "操作人用户ID（可选）")
        private String operatorId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DeviceBindingInfo {
        @Schema(description = "设备绑定信息")
        private AppUserDeviceEntity binding;
        @Schema(description = "设备状态: notActive-禁用, offline-离线, online-在线")
        private DeviceState state;

        public DeviceBindingInfo(AppUserDeviceEntity binding, DeviceState state) {
            this.binding = binding;
            this.state = state;
        }
    }

}
