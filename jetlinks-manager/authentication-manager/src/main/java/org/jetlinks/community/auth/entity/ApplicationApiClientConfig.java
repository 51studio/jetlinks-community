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
package org.jetlinks.community.auth.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * API客户端配置
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@Setter
public class ApplicationApiClientConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String baseUrl;
    private List<NameValue> headers;
    private List<NameValue> parameters;
    private AuthConfig authConfig;

    @Getter
    @Setter
    public static class AuthConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private String type;
        private Bearer bearer;
        private Basic basic;
        private String token;
        private OAuth2 oauth2;
    }

    @Getter
    @Setter
    public static class Bearer implements Serializable {
        private static final long serialVersionUID = 1L;
        private String token;
    }

    @Getter
    @Setter
    public static class Basic implements Serializable {
        private static final long serialVersionUID = 1L;
        private String username;
        private String password;
    }

    @Getter
    @Setter
    public static class OAuth2 implements Serializable {
        private static final long serialVersionUID = 1L;
        private String authorizationUrl;
        private String tokenUrl;
        private String redirectUri;
        private String clientId;
        private String clientSecret;
        private String grantType;
        private String accessTokenProperty;
        private String tokenRequestType;
    }

}
