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
     * <p>业务规则：</p>
     * <ul>
     *   <li>如果设备没有任何绑定关系，第一个绑定者自动升级为 manage 类型</li>
     *   <li>有且只有一个绑定者的 relationType 可以为 manage</li>
     * </ul>
     *
     * @param entity 绑定关系实体（userId、deviceId、deviceName、productId 已填充）
     * @return 绑定后的实体
     */
    public Mono<AppUserDeviceEntity> bindDevice(AppUserDeviceEntity entity) {
        // 默认类型为 bind
        if (entity.getRelationType() == null) {
            entity.setRelationType(AppUserDeviceRelationType.bind);
        }
        
        // 检查该设备是否已有绑定关系
        return this
            .createQuery()
            .where(AppUserDeviceEntity::getDeviceId, entity.getDeviceId())
            .count()
            .flatMap(count -> {
                // 如果是第一个绑定者，自动升级为 manage
                if (count == 0) {
                    entity.setRelationType(AppUserDeviceRelationType.manage);
                    entity.setOperatorId(null); // manage 类型 operatorId 应为空
                    return this
                        .insert(Mono.just(entity))
                        .thenReturn(entity);
                }
                
                // 检查是否已经绑定过
                return this
                    .createQuery()
                    .where(AppUserDeviceEntity::getUserId, entity.getUserId())
                    .and(AppUserDeviceEntity::getDeviceId, entity.getDeviceId())
                    .count()
                    .flatMap(userBindCount -> {
                        if (userBindCount > 0) {
                            return Mono.error(new BusinessException("error.app_user_device_already_bound", 409));
                        }
                        return this
                            .insert(Mono.just(entity))
                            .thenReturn(entity);
                    });
            });
    }

    /**
     * 解绑设备
     * <p>业务规则：</p>
     * <ul>
     *   <li>relationType 为 manage 的绑定人不能直接解绑</li>
     *   <li>需要先通过 {@link #transferManage(String, String, String)} 转移管理权限</li>
     * </ul>
     *
     * @param userId   C 端用户 ID
     * @param deviceId 设备 ID
     */
    public Mono<Void> unbindDevice(String userId, String deviceId) {
        return assertOwnership(userId, deviceId)
            .flatMap(binding -> {
                // 管理者不能直接解绑
                if (binding.getRelationType() == AppUserDeviceRelationType.manage) {
                    return Mono.error(new BusinessException(
                        "error.app_user_device_manage_cannot_unbind", 
                        403,
                        "管理者不能直接解绑，请先转移管理权限"));
                }
                return this
                    .createDelete()
                    .where(AppUserDeviceEntity::getUserId, userId)
                    .and(AppUserDeviceEntity::getDeviceId, deviceId)
                    .execute()
                    .then();
            });
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

    // -----------------------------------------------------------------------
    // 管理权限转移
    // -----------------------------------------------------------------------

    /**
     * 转移设备管理权限
     * <p>将当前用户的管理权限转移给另一个已绑定该设备的用户。</p>
     * <p>业务规则：</p>
     * <ul>
     *   <li>当前用户必须是 manage 类型</li>
     *   <li>目标用户必须是该设备的 bind 类型绑定者</li>
     *   <li>转移后，当前用户变为 bind 类型，目标用户变为 manage 类型</li>
     * </ul>
     *
     * @param currentManagerUserId 当前管理者用户 ID
     * @param deviceId             设备 ID
     * @param newManagerUserId     新管理者用户 ID（必须是 bind 类型的绑定者）
     * @return 转移完成后的 Mono
     */
    public Mono<Void> transferManage(String currentManagerUserId, String deviceId, String newManagerUserId) {
        // 验证当前用户是管理者
        return assertOwnership(currentManagerUserId, deviceId)
            .flatMap(currentBinding -> {
                if (currentBinding.getRelationType() != AppUserDeviceRelationType.manage) {
                    return Mono.error(new BusinessException(
                        "error.app_user_device_not_manager", 
                        403,
                        "只有管理者才能转移管理权限"));
                }
                
                // 验证目标用户是 bind 类型的绑定者
                return getBinding(newManagerUserId, deviceId)
                    .switchIfEmpty(Mono.error(new BusinessException(
                        "error.app_user_device_target_not_bound", 
                        404,
                        "目标用户未绑定该设备")))
                    .flatMap(targetBinding -> {
                        if (targetBinding.getRelationType() != AppUserDeviceRelationType.bind) {
                            return Mono.error(new BusinessException(
                                "error.app_user_device_target_not_bind_type", 
                                400,
                                "只能将管理权限转移给 bind 类型的绑定者"));
                        }
                        
                        // 执行权限转移：当前管理者降为 bind，目标用户升级为 manage
                        return this
                            .createUpdate()
                            .set(AppUserDeviceEntity::getRelationType, AppUserDeviceRelationType.bind)
                            .set(AppUserDeviceEntity::getOperatorId, newManagerUserId)
                            .where(AppUserDeviceEntity::getUserId, currentManagerUserId)
                            .and(AppUserDeviceEntity::getDeviceId, deviceId)
                            .execute()
                            .then(this
                                .createUpdate()
                                .set(AppUserDeviceEntity::getRelationType, AppUserDeviceRelationType.manage)
                                .set(AppUserDeviceEntity::getOperatorId, null)
                                .where(AppUserDeviceEntity::getUserId, newManagerUserId)
                                .and(AppUserDeviceEntity::getDeviceId, deviceId)
                                .execute()
                                .then());
                    });
            });
    }

    /**
     * 查询设备的所有绑定关系
     *
     * @param deviceId 设备 ID
     * @return 绑定关系列表
     */
    public Flux<AppUserDeviceEntity> getByDeviceId(String deviceId) {
        return this
            .createQuery()
            .where(AppUserDeviceEntity::getDeviceId, deviceId)
            .fetch();
    }

    /**
     * 查询设备的管理者
     *
     * @param deviceId 设备 ID
     * @return 管理者绑定关系（如果存在）
     */
    public Mono<AppUserDeviceEntity> getDeviceManager(String deviceId) {
        return this
            .createQuery()
            .where(AppUserDeviceEntity::getDeviceId, deviceId)
            .and(AppUserDeviceEntity::getRelationType, AppUserDeviceRelationType.manage)
            .fetchOne();
    }

}
