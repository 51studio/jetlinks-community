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
import org.jetlinks.community.auth.entity.AppUserDeviceEntity;
import org.jetlinks.community.auth.enums.AppUserDeviceRelationType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * C 端用户设备绑定服务
 *
 * @author jetlinks
 * @since 2.3
 */
@Slf4j
@Service
@AllArgsConstructor
public class AppUserDeviceService extends GenericReactiveCrudService<AppUserDeviceEntity, String> {

    // -----------------------------------------------------------------------
    // 绑定/解绑
    // -----------------------------------------------------------------------

    /**
     * 绑定设备
     * <p>调用方需传入 deviceName、productId（从 device-manager 模块查询后填充）。</p>
     * <p>relationType 默认为 {@link AppUserDeviceRelationType#bind}；
     *    bind/share 类型需传入 operatorId；
     *    manage 类型 operatorId 应为空。</p>
     *
     * @param entity 绑定关系实体（userId、deviceId、deviceName、productId 已填充）
     * @return 绑定后的实体
     */
    public Mono<AppUserDeviceEntity> bindDevice(AppUserDeviceEntity entity) {
        // 默认类型为 bind
        if (entity.getRelationType() == null) {
            entity.setRelationType(AppUserDeviceRelationType.bind);
        }
        return this
            .createQuery()
            .where(AppUserDeviceEntity::getUserId, entity.getUserId())
            .and(AppUserDeviceEntity::getDeviceId, entity.getDeviceId())
            .count()
            .flatMap(count -> {
                if (count > 0) {
                    return Mono.error(new BusinessException("error.app_user_device_already_bound", 409));
                }
                return this
                    .insert(Mono.just(entity))
                    .thenReturn(entity);
            });
    }

    /**
     * 解绑设备
     *
     * @param userId   C 端用户 ID
     * @param deviceId 设备 ID
     */
    public Mono<Void> unbindDevice(String userId, String deviceId) {
        return assertOwnership(userId, deviceId)
            .then(this
                .createDelete()
                .where(AppUserDeviceEntity::getUserId, userId)
                .and(AppUserDeviceEntity::getDeviceId, deviceId)
                .execute()
                .then());
    }

    // -----------------------------------------------------------------------
    // 查询
    // -----------------------------------------------------------------------

    /**
     * 查询指定用户绑定的全部设备
     *
     * @param userId C 端用户 ID
     * @return 设备关联列表
     */
    public Flux<AppUserDeviceEntity> getByUserId(String userId) {
        return this
            .createQuery()
            .where(AppUserDeviceEntity::getUserId, userId)
            .fetch();
    }

    /**
     * 根据 userId + deviceId 查询绑定关系
     */
    public Mono<AppUserDeviceEntity> getBinding(String userId, String deviceId) {
        return this
            .createQuery()
            .where(AppUserDeviceEntity::getUserId, userId)
            .and(AppUserDeviceEntity::getDeviceId, deviceId)
            .fetchOne();
    }

    // -----------------------------------------------------------------------
    // 归属校验
    // -----------------------------------------------------------------------

    /**
     * 校验设备是否属于该用户，不属于则抛出 403 异常
     *
     * @param userId   C 端用户 ID
     * @param deviceId 设备 ID
     */
    public Mono<AppUserDeviceEntity> assertOwnership(String userId, String deviceId) {
        return getBinding(userId, deviceId)
            .switchIfEmpty(Mono.error(
                new BusinessException("error.app_user_device_not_bound", HttpStatus.FORBIDDEN.value())));
    }

    // -----------------------------------------------------------------------
    // 更新
    // -----------------------------------------------------------------------

    /**
     * 更新绑定设备的备注
     *
     * @param userId      用户 ID
     * @param deviceId    设备 ID
     * @param description 新备注
     */
    public Mono<Void> updateDescription(String userId, String deviceId, String description) {
        return assertOwnership(userId, deviceId)
            .then(this
                .createUpdate()
                .set(AppUserDeviceEntity::getDescription, description)
                .where(AppUserDeviceEntity::getUserId, userId)
                .and(AppUserDeviceEntity::getDeviceId, deviceId)
                .execute()
                .then());
    }

    /**
     * 更新绑定设备的备注和/或关联类型
     *
     * @param userId       用户 ID
     * @param deviceId     设备 ID
     * @param description  新备注（为 null 则不更新）
     * @param relationType 新关联类型（为 null 则不更新）
     * @param operatorId   操作人 ID（为 null 则不更新）
     */
    public Mono<Void> updateBinding(String userId, String deviceId,
                                    String description,
                                    AppUserDeviceRelationType relationType,
                                    String operatorId) {
        return assertOwnership(userId, deviceId)
            .then(Mono.defer(() -> {
                var update = this.createUpdate();
                boolean hasUpdate = false;
                if (description != null) {
                    update.set(AppUserDeviceEntity::getDescription, description);
                    hasUpdate = true;
                }
                if (relationType != null) {
                    update.set(AppUserDeviceEntity::getRelationType, relationType);
                    hasUpdate = true;
                }
                if (operatorId != null) {
                    update.set(AppUserDeviceEntity::getOperatorId, operatorId);
                    hasUpdate = true;
                }
                if (!hasUpdate) {
                    return Mono.empty();
                }
                return update
                    .where(AppUserDeviceEntity::getUserId, userId)
                    .and(AppUserDeviceEntity::getDeviceId, deviceId)
                    .execute()
                    .then();
            }));
    }

}
