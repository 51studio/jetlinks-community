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
package org.jetlinks.community.configure.doc;

import io.swagger.v3.oas.models.Operation;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.annotation.CreateAction;
import org.hswebframework.web.authorization.annotation.DeleteAction;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.ResourceAction;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.jetlinks.community.web.permission.ApiOperationPermissionMappingService;
import org.springdoc.api.AbstractOpenApiResource;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.service.OpenAPIService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在 SpringDoc 生成 OpenAPI 文档时，自动建立 operationId -> (resourceId, actions) 的映射。
 * <p>
 * 同时实现 {@link ApplicationRunner}，在应用启动时强制触发一次 SpringDoc 构建，
 * 避免 WebFlux 环境下 SpringDoc 懒加载导致映射表始终为空的问题。
 *
 * @author jetlinks
 * @since 2.11
 */
@Slf4j
public class ApiOperationPermissionMappingCustomizer implements GlobalOperationCustomizer, ApplicationRunner {

    private final ApiOperationPermissionMappingService mappingService;
    private final ApplicationContext applicationContext;

    public ApiOperationPermissionMappingCustomizer(ApiOperationPermissionMappingService mappingService,
                                                     ApplicationContext applicationContext) {
        this.mappingService = mappingService;
        this.applicationContext = applicationContext;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (operation.getOperationId() == null) {
            return operation;
        }

        Resource resource = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), Resource.class);
        if (resource == null) {
            return operation;
        }

        Method method = handlerMethod.getMethod();
        Set<String> actions = new HashSet<>();

        if (AnnotatedElementUtils.hasAnnotation(method, QueryAction.class)) {
            actions.add(org.hswebframework.web.authorization.Permission.ACTION_QUERY);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, SaveAction.class)) {
            actions.add(org.hswebframework.web.authorization.Permission.ACTION_SAVE);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, DeleteAction.class)) {
            actions.add(org.hswebframework.web.authorization.Permission.ACTION_DELETE);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, CreateAction.class)) {
            actions.add(org.hswebframework.web.authorization.Permission.ACTION_ADD);
        }

        ResourceAction resourceAction = AnnotatedElementUtils.findMergedAnnotation(method, ResourceAction.class);
        if (resourceAction != null && actions.isEmpty()) {
            actions.add(resourceAction.id());
        }

        if (!actions.isEmpty()) {
            log.debug("Register API operation permission mapping: {} -> {}:{}",
                operation.getOperationId(), resource.id(), actions);
            mappingService.register(operation.getOperationId(), resource.id(), actions);
        }

        return operation;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, AbstractOpenApiResource> resources = applicationContext.getBeansOfType(AbstractOpenApiResource.class);
        if (resources.isEmpty()) {
            log.warn("No AbstractOpenApiResource bean found, API operation permission mapping will not be initialized at startup.");
            return;
        }

        for (Map.Entry<String, AbstractOpenApiResource> entry : resources.entrySet()) {
            try {
                Field openAPIServiceField = AbstractOpenApiResource.class.getDeclaredField("openAPIService");
                openAPIServiceField.setAccessible(true);
                OpenAPIService openAPIService = (OpenAPIService) openAPIServiceField.get(entry.getValue());
                if (openAPIService != null) {
                    log.info("Initializing API operation permission mapping for group: {}", entry.getKey());
                    openAPIService.build(Locale.getDefault());
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                log.warn("Failed to initialize API operation permission mapping for group {}: {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("API operation permission mapping initialized. Total mappings: {}",
            mappingService.getMappingCount());
    }
}
