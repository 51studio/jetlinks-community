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
import org.apache.commons.collections4.CollectionUtils;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.jetlinks.community.auth.entity.AssetBindingEntity;
import org.jetlinks.community.auth.web.request.AssetBindRequest;
import org.jetlinks.community.auth.web.request.AssetPermissionUpdateRequest;
import org.jetlinks.community.auth.web.response.AssetBindingInfo;
import org.jetlinks.community.auth.web.response.AssetPermissionDetail;
import org.jetlinks.community.auth.web.response.AssetPermissionInfo;
import org.jetlinks.community.auth.web.response.AssetTargetBindingInfo;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 资产绑定服务
 * <p>
 * 管理资产(设备/产品)与目标(组织)之间的绑定关系及权限
 *
 * @author jetlinks
 * @since 2.11
 */
@Service
@AllArgsConstructor
public class AssetBindingService extends GenericReactiveCrudService<AssetBindingEntity, String> {

    /**
     * 固定的权限字典: read/查看, save/编辑, delete/删除, share/共享
     */
    private static final List<AssetPermissionInfo> PERMISSION_DICT = Arrays.asList(
        new AssetPermissionInfo("read", "查看"),
        new AssetPermissionInfo("save", "编辑"),
        new AssetPermissionInfo("delete", "删除"),
        new AssetPermissionInfo("share", "共享")
    );

    /**
     * 绑定资产到组织 (upsert模式: 存在则更新权限，不存在则插入)
     *
     * @param assetType 资产类型 (device/product)
     * @param request   绑定请求
     * @return 受影响的记录数
     */
    public Mono<Integer> bindAssets(String assetType, AssetBindRequest request) {
        if (CollectionUtils.isEmpty(request.getAssetIdList())) {
            return Mono.just(0);
        }
        return Flux.fromIterable(request.getAssetIdList())
            .flatMap(assetId -> this.createQuery()
                .where(AssetBindingEntity::getAssetType, assetType)
                .and(AssetBindingEntity::getAssetId, assetId)
                .and(AssetBindingEntity::getTargetType, request.getTargetType())
                .and(AssetBindingEntity::getTargetId, request.getTargetId())
                .fetchOne()
                .flatMap(existing ->
                    this.createUpdate()
                        .set(AssetBindingEntity::getPermission, request.getPermission())
                        .where(AssetBindingEntity::getId, existing.getId())
                        .execute()
                )
                .switchIfEmpty(Mono.defer(() -> {
                    AssetBindingEntity entity = new AssetBindingEntity();
                    entity.setAssetType(assetType);
                    entity.setAssetId(assetId);
                    entity.setTargetType(request.getTargetType());
                    entity.setTargetId(request.getTargetId());
                    entity.setPermission(request.getPermission());
                    return this.insert(Mono.just(entity));
                }))
            )
            .reduce(0, Integer::sum);
    }

    /**
     * 全量绑定(覆盖式): 删除资产对应 targetType 的所有旧绑定，插入新绑定
     *
     * @param assetType  资产类型
     * @param assetId    资产ID
     * @param targetType 目标类型
     * @param requests   新的绑定列表
     */
    public Mono<Void> bindAssetsAll(String assetType, String assetId, String targetType, List<AssetBindRequest> requests) {
        return this.createDelete()
            .where(AssetBindingEntity::getAssetType, assetType)
            .and(AssetBindingEntity::getAssetId, assetId)
            .and(AssetBindingEntity::getTargetType, targetType)
            .execute()
            .then(
                CollectionUtils.isEmpty(requests)
                    ? Mono.empty()
                    : Flux.fromIterable(requests)
                        .map(req -> {
                            AssetBindingEntity entity = new AssetBindingEntity();
                            entity.setAssetType(assetType);
                            entity.setAssetId(assetId);
                            entity.setTargetType(targetType);
                            entity.setTargetId(req.getTargetId());
                            entity.setPermission(req.getPermission());
                            return entity;
                        })
                        .as(this::insert)
                        .then()
            );
    }

    /**
     * 解绑资产
     *
     * @param assetType 资产类型
     * @param request   解绑请求
     * @return 删除的记录数
     */
    public Mono<Integer> unbindAssets(String assetType, AssetBindRequest request) {
        if (CollectionUtils.isEmpty(request.getAssetIdList())) {
            return Mono.just(0);
        }
        return this.createDelete()
            .where(AssetBindingEntity::getAssetType, assetType)
            .and(AssetBindingEntity::getTargetType, request.getTargetType())
            .and(AssetBindingEntity::getTargetId, request.getTargetId())
            .in(AssetBindingEntity::getAssetId, request.getAssetIdList())
            .execute();
    }

    /**
     * 查询资产绑定的所有目标及权限
     *
     * @param assetType  资产类型
     * @param assetId    资产ID
     * @param targetType 目标类型
     * @param targetIds  目标ID列表(为空则查全部)
     * @return 目标绑定信息列表
     */
    public Flux<AssetTargetBindingInfo> queryTargetBindings(String assetType, String assetId,
                                                            String targetType, List<String> targetIds) {
        return this.createQuery()
            .where(AssetBindingEntity::getAssetType, assetType)
            .and(AssetBindingEntity::getAssetId, assetId)
            .and(AssetBindingEntity::getTargetType, targetType)
            .when(CollectionUtils.isNotEmpty(targetIds), q -> q.in(AssetBindingEntity::getTargetId, targetIds))
            .fetch()
            .map(e -> new AssetTargetBindingInfo(e.getTargetId(), e.getPermission()));
    }

    /**
     * 查询组织下资产的绑定权限
     *
     * @param assetType 资产类型
     * @param orgId     组织ID
     * @param assetIds  资产ID列表(为空则查全部)
     * @return 资产绑定信息列表
     */
    public Flux<AssetBindingInfo> queryAssetBindingsByOrg(String assetType, String orgId, List<String> assetIds) {
        return this.createQuery()
            .where(AssetBindingEntity::getAssetType, assetType)
            .and(AssetBindingEntity::getTargetType, "org")
            .and(AssetBindingEntity::getTargetId, orgId)
            .when(CollectionUtils.isNotEmpty(assetIds), q -> q.in(AssetBindingEntity::getAssetId, assetIds))
            .fetch()
            .map(e -> new AssetBindingInfo(e.getAssetId(), e.getPermission()));
    }

    /**
     * 查询资产可用权限信息
     *
     * @param assetType 资产类型
     * @param assetIds  资产ID列表
     * @return 资产权限详情列表
     */
    public Flux<AssetPermissionDetail> queryAssetPermissions(String assetType, List<String> assetIds) {
        if (CollectionUtils.isEmpty(assetIds)) {
            return Flux.empty();
        }
        return Flux.fromIterable(assetIds)
            .map(assetId -> new AssetPermissionDetail(assetId, PERMISSION_DICT));
    }

    /**
     * 获取权限字典
     *
     * @return 权限字典列表
     */
    public Flux<AssetPermissionInfo> getPermissionDict() {
        return Flux.fromIterable(PERMISSION_DICT);
    }

    /**
     * 批量更新指定组织下资产的权限
     *
     * @param assetType 资产类型
     * @param orgId     组织ID
     * @param request   权限更新请求
     * @return 受影响的记录数
     */
    public Mono<Integer> batchUpdatePermissions(String assetType, String orgId, AssetPermissionUpdateRequest request) {
        if (CollectionUtils.isEmpty(request.getAssetIdList())) {
            return Mono.just(0);
        }
        return this.createUpdate()
            .set(AssetBindingEntity::getPermission, request.getPermission())
            .where(AssetBindingEntity::getAssetType, assetType)
            .and(AssetBindingEntity::getTargetType, "org")
            .and(AssetBindingEntity::getTargetId, orgId)
            .in(AssetBindingEntity::getAssetId, request.getAssetIdList())
            .execute();
    }
}
