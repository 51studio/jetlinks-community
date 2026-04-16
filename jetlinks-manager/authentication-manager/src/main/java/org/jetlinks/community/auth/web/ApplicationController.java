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
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.jetlinks.community.auth.entity.ApplicationEntity;

import org.jetlinks.community.auth.enums.ApplicationProvider;
import org.jetlinks.community.auth.service.ApplicationService;
import org.jetlinks.community.auth.web.request.ApplicationSaveRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 应用管理接口
 * <p>
 * 提供独立的应用管理功能，支持应用分组和应用类型（单点登录、第三方应用）
 *
 * @author jetlinks
 * @since 2.11
 */
@RestController
@RequestMapping("/application")
@AllArgsConstructor
@Resource(id = "application", name = "应用管理", group = "system")
@Tag(name = "应用管理")
public class ApplicationController {

    private final ApplicationService applicationService;

    /**
     * 获取应用类型/提供商列表（前端新增/编辑时选择应用类型）
     */
    @GetMapping("/providers")
    @QueryAction
    @Operation(summary = "获取应用类型列表")
    public Mono<List<ApplicationProvider>> getProviders() {
        return Mono.just(Arrays.asList(ApplicationProvider.values()));
    }

    /**
     * 新增应用
     */
    @PostMapping
    @SaveAction
    @Operation(summary = "新增应用")
    public Mono<ApplicationEntity> add(@RequestBody Mono<ApplicationSaveRequest> body) {
        return body.flatMap(applicationService::createApplication);
    }

    /**
     * 分页查询应用列表
     */
    @PostMapping("/_query")
    @QueryAction
    @Operation(summary = "分页查询应用")
    public Mono<PagerResult<ApplicationEntity>> query(@RequestBody Mono<QueryParamEntity> query) {
        return query.flatMap(applicationService::queryPager);
    }

    /**
     * 获取应用详情
     */
    @GetMapping("/{id}")
    @QueryAction
    @Operation(summary = "获取应用详情")
    public Mono<ApplicationEntity> getById(@PathVariable @Parameter(description = "应用ID") String id) {
        return applicationService.findDetailById(id);
    }

    /**
     * 修改应用
     */
    @PutMapping("/{id}")
    @SaveAction
    @Operation(summary = "修改应用")
    public Mono<Integer> update(
        @PathVariable @Parameter(description = "应用ID") String id,
        @RequestBody Mono<ApplicationSaveRequest> body) {
        return body.flatMap(entity -> applicationService.updateApplication(id, entity));
    }

    /**
     * 删除应用
     */
    @DeleteMapping("/{id}")
    @SaveAction
    @Operation(summary = "删除应用")
    public Mono<Integer> delete(@PathVariable @Parameter(description = "应用ID") String id) {
        return applicationService.deleteById(id);
    }

    /**
     * 获取可授权的接口列表
     */
    @GetMapping("/operations")
    @QueryAction
    @Operation(summary = "获取可授权的接口列表")
    public Mono<List<String>> getOperations() {
        return applicationService.getOperations();
    }

    /**
     * 新增可授权的接口
     */
    @PatchMapping("/operations/_batch")
    @SaveAction
    @Operation(summary = "新增可授权的接口")
    public Mono<Void> addOperations(@RequestBody List<String> operations) {
        BatchOperationsRequest request = new BatchOperationsRequest();
        request.setOperations(operations);
        return applicationService.addOperations(request);
    }

    /**
     * 删除可授权的接口
     */
    @DeleteMapping("/operations/_batch")
    @SaveAction
    @Operation(summary = "删除可授权的接口")
    public Mono<Void> deleteOperations(@RequestBody List<String> operations) {
        BatchOperationsRequest request = new BatchOperationsRequest();
        request.setOperations(operations);
        return applicationService.deleteOperations(request);
    }

    /**
     * 获取应用已授权的接口ID列表
     */
    @GetMapping("/{id}/granted")
    @QueryAction
    @Operation(summary = "获取应用已授权的接口ID列表")
    public Mono<List<String>> getGrantedApis(@PathVariable @Parameter(description = "应用ID") String id) {
        return applicationService.getGrantedApis(id);
    }

    /**
     * 赋权-为应用勾选/取消选中API
     */
    @PostMapping("/{id}/grant")
    @SaveAction
    @Operation(summary = "为应用赋权（勾选/取消选中API）")
    public Mono<Void> grantApis(
        @PathVariable @Parameter(description = "应用ID") String id,
        @RequestBody Mono<GrantRequest> request) {
        return request.flatMap(r -> applicationService.grantApis(id, r.getOperations()));
    }

    @Getter
    @Setter
    public static class BatchOperationsRequest {
        @Schema(description = "接口ID列表")
        private List<String> operations;
    }

    @Getter
    @Setter
    public static class GrantRequest {
        @Schema(description = "接口授权信息列表")
        private List<GrantOperation> operations;
    }

    @Getter
    @Setter
    public static class GrantOperation {
        @Schema(description = "接口ID")
        private String id;
        @Schema(description = "权限列表")
        private List<String> permissions;
    }

}
