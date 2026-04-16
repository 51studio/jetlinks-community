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
 * OAuth2 Token 响应
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2TokenResponse {

    @Schema(description = "访问令牌")
    private String access_token;

    @Schema(description = "令牌类型")
    private String token_type;

    @Schema(description = "过期时间（秒）")
    private long expires_in;

    public static OAuth2TokenResponse of(String accessToken, String tokenType, long expiresIn) {
        return new OAuth2TokenResponse(accessToken, tokenType, expiresIn);
    }
}
