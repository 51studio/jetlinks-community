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

/**
 * API 客户端密钥响应（仅生成/重置时返回 SecretKey 明文）
 *
 * @author jetlinks
 * @since 2.3
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiClientKeyResponse {

    @Schema(description = "客户端ID")
    private String clientId;

    @Schema(description = "SecretKey 明文（仅此次返回，请妥善保存）")
    private String secretKey;

    public static ApiClientKeyResponse of(String clientId, String secretKey) {
        return new ApiClientKeyResponse(clientId, secretKey);
    }
}
