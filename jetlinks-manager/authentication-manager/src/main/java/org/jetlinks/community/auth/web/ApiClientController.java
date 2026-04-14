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
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.annotation.DeleteAction;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceCrudController;
import org.jetlinks.community.auth.entity.ApiClientAccessLogEntity;
import org.jetlinks.community.auth.entity.ApiClientEntity;
import org.jetlinks.community.auth.service.ApiClientAccessLogService;
import org.jetlinks.community.auth.service.ApiClientService;
import org.jetlinks.community.auth.service.ApiClientTokenService;
import org.jetlinks.community.auth.service.OrganizationService;
import org.jetlinks.community.auth.service.RoleService;
import org.jetlinks.community.auth.web.response.ApiClientKeyResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * API 客户端（第三方系统对接）管理接口
 *
 * @author jetlinks
 * @since 2.3
 */
@RestController
@RequestMapping("/open-api/client")
@AllArgsConstructor
@Resource(id = "open-api", name = "应用管理", group = "system")
@Tag(name = "第三方系统对接（API客户端）")
public class ApiClientController implements ReactiveServiceCrudController<ApiClientEntity, String> {

    private final ApiClientService apiClientService;
    private final ApiClientAccessLogService accessLogService;
    private final ApiClientTokenService tokenService;
    private final RoleService roleService;
    private final OrganizationService organizationService;

    @Override
    public ApiClientService getService() {
        return apiClientService;
    }

    /**
     * 新增 API 客户端，自动生成 SecretId（AccessKey）
     */
    @PostMapping
    @SaveAction
    @Operation(summary = "新增API客户端")
    public Mono<ApiClientEntity> add(@RequestBody Mono<ApiClientEntity> body) {
        return body.flatMap(apiClientService::createClient);
    }

    /**
     * 启用 API 客户端
     */
    @PostMapping("/{id}/_enable")
    @SaveAction
    @Operation(summary = "启用API客户端")
    public Mono<Void> enable(@PathVariable @Parameter(description = "客户端ID") String id) {
        return apiClientService.enable(id);
    }

    /**
     * 禁用 API 客户端
     */
    @PostMapping("/{id}/_disable")
    @SaveAction
    @Operation(summary = "禁用API客户端")
    public Mono<Void> disable(@PathVariable @Parameter(description = "客户端ID") String id) {
        return apiClientService.disable(id);
    }

    /**
     * 生成/重置 AccessKey + SecretKey
     * SecretKey 明文仅此次响应中返回，请妥善保存
     */
    @PostMapping("/{id}/_generate-keys")
    @SaveAction
    @Operation(summary = "生成/重置密钥（SecretKey明文仅此次返回）")
    public Mono<ApiClientKeyResponse> generateKeys(@PathVariable @Parameter(description = "客户端ID") String id) {
        return apiClientService.generateKeys(id);
    }

    /**
     * 为 API 客户端颁发 Bearer Token（TTL 24h）
     */
    @PostMapping("/{id}/_issue-token")
    @SaveAction
    @Operation(summary = "颁发Bearer Token（TTL 24h）")
    public Mono<String> issueToken(@PathVariable @Parameter(description = "客户端ID") String id) {
        return tokenService.issueToken(id);
    }

    /**
     * 查询指定客户端的调用记录（分页）
     */
    @PostMapping("/{id}/access-log/_query")
    @QueryAction
    @Operation(summary = "查询API客户端调用记录")
    public Mono<PagerResult<ApiClientAccessLogEntity>> queryAccessLog(
        @PathVariable @Parameter(description = "客户端ID") String id,
        @RequestBody Mono<QueryParamEntity> query) {
        return query.flatMap(q -> accessLogService.queryByClientId(id, q));
    }

    @PostMapping("/{clientId}/role/_bind")
    @SaveAction
    @Operation(summary = "绑定角色")
    public Mono<Void> bindRole(@PathVariable @Parameter(description = "客户端ID") String clientId,
                           @RequestBody Mono<List<String>> roleIdList) {
        return roleIdList
            .flatMap(list -> roleService.bindUser(Collections.singleton(clientId), list, false));
    }

    @PostMapping("/{clientId}/role/_unbind")
    @SaveAction
    @Operation(summary = "解绑角色")
    public Mono<Void> unbindRole(@PathVariable @Parameter(description = "客户端ID") String clientId,
                                @RequestBody Mono<List<String>> roleIdList) {
        return roleIdList
            .flatMap(list -> roleService.unbindUser(Collections.singleton(clientId), list));
    }

    @PostMapping("/{clientId}/org/_bind")
    @SaveAction
    @Operation(summary = "绑定组织")
    public Mono<Void> bindOrg(@PathVariable @Parameter(description = "客户端ID") String clientId,
                           @RequestBody Mono<BindOrgRequest> request) {
        return request
            .flatMap(req -> organizationService.bindUser(
                clientId,
                req.getOrgIds() != null ? req.getOrgIds() : Collections.emptyList()))
            .then();
    }

    @PostMapping("/{clientId}/org/_unbind")
    @SaveAction
    @Operation(summary = "解绑组织")
    public Mono<Void> unbindOrg(@PathVariable @Parameter(description = "客户端ID") String clientId,
                                @RequestBody Mono<BindOrgRequest> request) {
        return request
            .flatMap(req -> organizationService.unbindUser(
                clientId,
                req.getOrgIds() != null ? req.getOrgIds() : Collections.emptyList()))
            .then();
    }

    @Getter
    @Setter
    public static class BindOrgRequest {
        private List<String> orgIds;
    }

}
