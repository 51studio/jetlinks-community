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
package org.jetlinks.community.auth.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 资产绑定/解绑请求
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@Setter
public class AssetBindRequest {

    @Schema(description = "目标类型(org)")
    private String targetType;

    @Schema(description = "目标ID(组织ID)")
    private String targetId;

    @Schema(description = "资产类型(device/product)")
    private String assetType;

    @Schema(description = "资产ID列表")
    private List<String> assetIdList;

    @Schema(description = "权限列表(read/save/delete/share)")
    private List<String> permission;
}
