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
 * 单点登录配置
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@Setter
public class ApplicationSsoConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private SsoConfiguration configuration;
    private Boolean autoCreateUser;
    private String usernamePrefix;
    private List<String> roleIdList;
    private List<String> orgIdList;
    private String defaultPasswd;

    @Getter
    @Setter
    public static class SsoConfiguration implements Serializable {
        private static final long serialVersionUID = 1L;
        private String type;
        private OAuth2 oauth2;
        private Bearer bearer;
        private String appId;
        private String appKey;
        private String appSecret;
    }

    @Getter
    @Setter
    public static class OAuth2 implements Serializable {
        private static final long serialVersionUID = 1L;
        private String authorizationUrl;
        private String redirectUri;
        private String clientId;
        private String clientSecret;
        private String userInfoUrl;
        private String scope;
        private String logoUrl;
        private UserProperty userProperty;
        private String grantType;
        private String tokenUrl;
        private String accessTokenProperty;
        private String tokenRequestType;
    }

    @Getter
    @Setter
    public static class Bearer implements Serializable {
        private static final long serialVersionUID = 1L;
        private String loginUrl;
        private String userInfoUrl;
        private String tokenKey;
        private Boolean base64;
        private UserProperty userProperty;
    }

    @Getter
    @Setter
    public static class UserProperty implements Serializable {
        private static final long serialVersionUID = 1L;
        private String userId;
        private String username;
        private String name;
        private String avatar;
        private String email;
        private String telephone;
        private String description;
    }

}
