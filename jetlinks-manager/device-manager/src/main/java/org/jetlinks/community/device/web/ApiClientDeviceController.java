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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.jetlinks.community.PropertyMetric;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.device.entity.DeviceProductEntity;
import org.jetlinks.community.device.entity.DeviceProperty;
import org.jetlinks.core.device.DeviceThingType;
import org.jetlinks.community.device.service.LocalDeviceInstanceService;
import org.jetlinks.community.device.service.LocalDeviceProductService;
import org.jetlinks.community.device.service.data.DeviceDataService;
import org.jetlinks.community.things.impl.metric.DefaultPropertyMetricManager;
import org.jetlinks.community.timeseries.query.AggregationData;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * API 客户端设备访问接口
 * <p>
 * 通过 API 客户端 Token 访问设备信息和属性数据，用于第三方系统对接。
 * </p>
 *
 * @author jetlinks
 * @since 2.3
 */
@RestController
@RequestMapping("/open-api/device")
@AllArgsConstructor
@Tag(name = "API客户端设备接口")
public class ApiClientDeviceController {

    private final LocalDeviceInstanceService deviceInstanceService;
    private final LocalDeviceProductService deviceProductService;
    private final DeviceDataService deviceDataService;
    private final DefaultPropertyMetricManager metricManager;
    private final org.jetlinks.community.auth.service.ApiClientTokenService apiClientTokenService;

    /**
     * 验证 Token 并返回客户端 ID
     */
    private Mono<String> validateToken(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header"));
        }
        String bearerToken = token.substring(7);
        return apiClientTokenService
            .getClientByToken(bearerToken)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token")))
            .flatMap(client -> {
                if (client.getState() != org.jetlinks.community.auth.enums.ApiClientState.enabled) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API client is disabled"));
                }
                return Mono.just(client.getId());
            });
    }

    /**
     * 根据设备ID获取设备详情
     */
    @GetMapping("/{deviceId}")
    @Operation(summary = "获取设备详情", description = "通过API客户端Token获取指定设备的详细信息")
    public Mono<DeviceInstanceEntity> getDeviceInfo(
        @RequestHeader("Authorization") @Parameter(description = "Bearer Token") String authorization,
        @PathVariable @Parameter(description = "设备ID") String deviceId) {

        return validateToken(authorization)
            .flatMap(clientId -> deviceInstanceService.findById(deviceId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"))));
    }

    /**
     * 根据产品ID获取产品信息
     */
    @GetMapping("/product/{productId}")
    @Operation(summary = "获取产品信息", description = "通过API客户端Token获取指定产品的详细信息")
    public Mono<DeviceProductEntity> getProductInfo(
        @RequestHeader("Authorization") @Parameter(description = "Bearer Token") String authorization,
        @PathVariable @Parameter(description = "产品ID") String productId) {

        return validateToken(authorization)
            .flatMap(clientId -> deviceProductService.findById(productId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"))));
    }

    @GetMapping("/product/{productId}/access-config")
    @Operation(summary = "获取产品接入配置", description = "获取产品的接入配置信息，包含MQTT认证配置等")
    public Mono<Map<String, Object>> getProductAccessConfig(
        @RequestHeader("Authorization") @Parameter(description = "Bearer Token") String authorization,
        @PathVariable @Parameter(description = "产品ID") String productId) {

        return validateToken(authorization)
            .flatMap(clientId -> deviceProductService.findById(productId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")))
                .map(product -> {
                    Map<String, Object> config = new java.util.HashMap<>();
                    config.put("accessId", product.getAccessId());
                    config.put("accessProvider", product.getAccessProvider());
                    config.put("accessName", product.getAccessName());
                    config.put("configuration", product.getConfiguration());
                    return config;
                }));
    }

    /**
     * 获取设备属性列表
     */
    @GetMapping("/{deviceId}/properties")
    @Operation(summary = "获取设备属性列表", description = "获取设备的最新属性数据")
    public Mono<List<DeviceProperty>> getDeviceProperties(
        @RequestHeader("Authorization") @Parameter(description = "Bearer Token") String authorization,
        @PathVariable @Parameter(description = "设备ID") String deviceId,
        @RequestParam(required = false) @Parameter(description = "属性ID列表，逗号分隔") String property) {

        return validateToken(authorization)
            .flatMapMany(clientId -> deviceDataService.queryProperty(deviceId, new QueryParamEntity(),
                property != null && !property.isEmpty() ? property.split(",") : new String[0]))
            .collectList();
    }

    /**
     * 查询设备属性历史数据（分页）
     */
    @PostMapping("/{deviceId}/property/{property}/_query")
    @Operation(summary = "查询设备属性历史数据", description = "分页查询指定属性的历史数据")
    public Mono<PagerResult<DeviceProperty>> queryDevicePropertyHistory(
        @RequestHeader("Authorization") @Parameter(description = "Bearer Token") String authorization,
        @PathVariable @Parameter(description = "设备ID") String deviceId,
        @PathVariable @Parameter(description = "属性ID") String property,
        @RequestBody Mono<QueryParamEntity> queryParam) {

        return validateToken(authorization)
            .flatMap(clientId -> queryParam.flatMap(param ->
                deviceDataService.queryPropertyPage(deviceId, param, property.split(","))));
    }

    /**
     * 获取设备属性指标数据
     */
    @GetMapping("/{deviceId}/metric/property/{property}")
    @Operation(summary = "获取设备属性指标数据", description = "获取指定属性的指标统计信息")
    public Flux<PropertyMetric> getPropertyMetric(
        @RequestHeader("Authorization") @Parameter(description = "Bearer Token") String authorization,
        @PathVariable @Parameter(description = "设备ID") String deviceId,
        @PathVariable @Parameter(description = "属性ID") String property) {

        return validateToken(authorization)
            .<PropertyMetric>flatMapMany(clientId ->
                                             metricManager.getPropertyMetrics(DeviceThingType.device.getId(), deviceId, property));
    }

    /**
     * 批量获取设备属性指标数据
     */
    @PostMapping("/{deviceId}/metric/properties")
    @Operation(summary = "批量获取设备属性指标数据", description = "批量获取多个属性的指标统计信息")
    public Flux<DefaultPropertyMetricManager.DevicePropertyMetricInfo> getPropertyMetrics(
        @RequestHeader("Authorization") @Parameter(description = "Bearer Token") String authorization,
        @PathVariable @Parameter(description = "设备ID") String deviceId,
        @RequestBody @Parameter(description = "属性ID列表") List<String> properties) {

        return validateToken(authorization)
            .flatMapMany(clientId ->
                metricManager.getPropertyMetrics(DeviceThingType.device.getId(), deviceId, properties));
    }

    /**
     * 聚合查询设备属性
     */
    @PostMapping("/{deviceId}/agg/_query")
    @Operation(summary = "聚合查询设备属性", description = "按时间聚合统计属性数据")
    public Flux<Map<String, Object>> aggDeviceProperty(
        @RequestHeader("Authorization") @Parameter(description = "Bearer Token") String authorization,
        @PathVariable @Parameter(description = "设备ID") String deviceId,
        @RequestBody Mono<AggRequest> param) {

        return validateToken(authorization)
            .flatMapMany(clientId -> param
                .flatMapMany(request -> deviceDataService
                    .aggregationPropertiesByDevice(deviceId,
                        request.getQuery(),
                        request.getColumns().toArray(new DeviceDataService.DevicePropertyAggregation[0]))
                    .map(AggregationData::values)));
    }

    @Getter
    @Setter
    public static class AggRequest {
        private DeviceDataService.AggregationRequest query;
        private List<DeviceDataService.DevicePropertyAggregation> columns;
    }
}
