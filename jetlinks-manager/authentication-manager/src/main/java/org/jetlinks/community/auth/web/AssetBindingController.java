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
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.ResourceAction;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceCrudController;
import org.jetlinks.community.auth.entity.AssetBindingEntity;
import org.jetlinks.community.auth.service.AssetBindingService;
import org.jetlinks.community.auth.web.request.AssetBindRequest;
import org.jetlinks.community.auth.web.request.AssetPermissionUpdateRequest;
import org.jetlinks.community.auth.web.response.AssetBindingInfo;
import org.jetlinks.community.auth.web.response.AssetPermissionDetail;
import org.jetlinks.community.auth.web.response.AssetPermissionInfo;
import org.jetlinks.community.auth.web.response.AssetTargetBindingInfo;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * 资产绑定控制器
 * <p>
 * 管理资产(设备/产品)与组织之间的绑定关系和权限。
 * 实现 {@link ReactiveServiceCrudController} 接口，自动提供标准 CRUD 端点（分页查询、保存、更新、删除等）。
 *
 * @author jetlinks
 * @since 2.11
 */
@RestController
@RequestMapping("/assets")
@AllArgsConstructor
@Getter
@Resource(id = "assets-bind", name = "资产绑定", group = "system")
@Tag(name = "资产绑定")
public class AssetBindingController implements ReactiveServiceCrudController<AssetBindingEntity, String> {

    private final AssetBindingService service;

    /**
     * 绑定资产到组织
     */
    @PostMapping("/bind/{assetType}")
    @ResourceAction(id = "bind", name = "绑定资产")
    @Operation(summary = "绑定资产到组织")
    public Mono<Integer> bindAssets(
        @PathVariable @Parameter(description = "资产类型(device/product)") String assetType,
        @RequestBody Mono<AssetBindRequest> request) {
        return request.flatMap(req -> service.bindAssets(assetType, req));
    }

    /**
     * 全量绑定资产(覆盖式): 删除旧绑定后插入新绑定
     */
    @PostMapping("/bind/{assetType}/{assetId}/{targetType}/_all")
    @ResourceAction(id = "bind", name = "绑定资产")
    @Operation(summary = "全量绑定资产(覆盖式)")
    public Mono<Void> bindAssetsAll(
        @PathVariable @Parameter(description = "资产类型") String assetType,
        @PathVariable @Parameter(description = "资产ID") String assetId,
        @PathVariable @Parameter(description = "目标类型(org)") String targetType,
        @RequestBody Mono<List<AssetBindRequest>> requests) {
        return requests.flatMap(list -> service.bindAssetsAll(assetType, assetId, targetType, list));
    }

    /**
     * 解绑资产
     */
    @PostMapping("/unbind/{assetType}")
    @ResourceAction(id = "unbind", name = "解绑资产")
    @Operation(summary = "解绑资产")
    public Mono<Integer> unbindAssets(
        @PathVariable @Parameter(description = "资产类型") String assetType,
        @RequestBody Mono<AssetBindRequest> request) {
        return request.flatMap(req -> service.unbindAssets(assetType, req));
    }

    /**
     * 查询资产绑定的所有目标及权限
     * <p>
     * 前端调用: getBindOrgAuthList(assetType, assetId, targetType, data=[])
     */
    @PostMapping("/bindings/{assetType}/{assetId}/{targetType}/target/_query")
    @ResourceAction(id = "query", name = "查询绑定")
    @Operation(summary = "查询资产绑定的所有目标")
    public Flux<AssetTargetBindingInfo> queryTargetBindings(
        @PathVariable @Parameter(description = "资产类型") String assetType,
        @PathVariable @Parameter(description = "资产ID") String assetId,
        @PathVariable @Parameter(description = "目标类型(org)") String targetType,
        @RequestBody Mono<List<String>> targetIds) {
        return targetIds
            .defaultIfEmpty(Collections.emptyList())
            .flatMapMany(ids -> service.queryTargetBindings(assetType, assetId, targetType, ids));
    }

    /**
     * 查询组织下资产的绑定权限
     * <p>
     * 前端调用: getPermission_api(type, ids, orgId)
     */
    @PostMapping("/bindings/{assetType}/org/{orgId}/_query")
    @ResourceAction(id = "query", name = "查询绑定")
    @Operation(summary = "查询组织下资产的绑定权限")
    public Flux<AssetBindingInfo> queryAssetBindingsByOrg(
        @PathVariable @Parameter(description = "资产类型") String assetType,
        @PathVariable @Parameter(description = "组织ID") String orgId,
        @RequestBody Mono<List<String>> assetIds) {
        return assetIds
            .defaultIfEmpty(Collections.emptyList())
            .flatMapMany(ids -> service.queryAssetBindingsByOrg(assetType, orgId, ids));
    }

    /**
     * 查询资产可用权限信息
     * <p>
     * 前端调用: getBindingsPermission(type, ids)
     */
    @PostMapping("/bindings/{assetType}")
    @ResourceAction(id = "query", name = "查询绑定")
    @Operation(summary = "查询资产可用权限信息")
    public Flux<AssetPermissionDetail> queryAssetPermissions(
        @PathVariable @Parameter(description = "资产类型") String assetType,
        @RequestBody Mono<List<String>> assetIds) {
        return assetIds
            .defaultIfEmpty(Collections.emptyList())
            .flatMapMany(ids -> service.queryAssetPermissions(assetType, ids));
    }

    /**
     * 获取权限字典
     * <p>
     * 前端调用: getPermissionDict_api()
     */
    @GetMapping("/bindings/{assetType}/permissions")
    @ResourceAction(id = "query", name = "查询绑定")
    @Operation(summary = "获取权限字典")
    public Flux<AssetPermissionInfo> getPermissionDict(
        @PathVariable @Parameter(description = "资产类型") String assetType) {
        return service.getPermissionDict();
    }

    /**
     * 批量更新资产权限
     * <p>
     * 前端调用: updatePermission_api(type, orgId, { assetIdList, permission })
     */
    @PutMapping("/permission/{assetType}/org/{orgId}/_batch")
    @ResourceAction(id = "permission", name = "权限管理")
    @Operation(summary = "批量更新资产权限")
    public Mono<Integer> batchUpdatePermissions(
        @PathVariable @Parameter(description = "资产类型") String assetType,
        @PathVariable @Parameter(description = "组织ID") String orgId,
        @RequestBody Mono<AssetPermissionUpdateRequest> request) {
        return request.flatMap(req -> service.batchUpdatePermissions(assetType, orgId, req));
    }
}
