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
package org.jetlinks.community.auth.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 资产绑定目标查询结果(查询资产绑定到哪些组织时返回)
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AssetTargetBindingInfo {

    @Schema(description = "目标ID(组织ID)")
    private String targetId;

    @Schema(description = "已授予的权限列表")
    private List<String> grantedPermissions;
}
