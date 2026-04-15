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
 * API服务配置
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@Setter
public class ApplicationApiServerConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用ID（与主键 id 一致，返回时动态注入，不持久化到JSON字段）
     */
    private String appId;

    /**
     * 密钥（与主表 secret_key 一致，返回时动态注入，不持久化到JSON字段）
     */
    private String secureKey;

    private String redirectUri;
    private List<String> roleIdList;
    private List<String> orgIdList;
    private String ipWhiteList;
    private String signature;
    private Boolean enableOAuth2;

}
