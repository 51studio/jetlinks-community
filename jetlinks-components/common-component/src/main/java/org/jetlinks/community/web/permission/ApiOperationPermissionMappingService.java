/*
 * Copyright 2025 JetLinks https://www.jetlinks.cn
 *
 * Licensed under the Apache License, Version 2.2 (the "License");
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
package org.jetlinks.community.web.permission;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swagger operationId 与 hswebframework 权限（resourceId + actions）的映射服务。
 * <p>
 * 在应用启动生成 OpenAPI 文档时由 {@code GlobalOperationCustomizer} 自动注册映射关系。
 *
 * @author jetlinks
 * @since 2.11
 */
@Component
public class ApiOperationPermissionMappingService {

    private final Map<String, ApiOperationPermission> mapping = new ConcurrentHashMap<>();

    public void register(String operationId, String resourceId, Set<String> actions) {
        mapping.put(operationId, ApiOperationPermission.of(resourceId, actions));
    }

    public Optional<ApiOperationPermission> resolve(String operationId) {
        return Optional.ofNullable(mapping.get(operationId));
    }

    public int getMappingCount() {
        return mapping.size();
    }
}
