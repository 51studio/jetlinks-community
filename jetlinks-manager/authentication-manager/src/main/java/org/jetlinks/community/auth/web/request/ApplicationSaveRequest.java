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

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.auth.entity.ApplicationApiClientConfig;
import org.jetlinks.community.auth.entity.ApplicationApiServerConfig;
import org.jetlinks.community.auth.entity.ApplicationPageConfig;
import org.jetlinks.community.auth.entity.ApplicationSsoConfig;

import java.io.Serializable;
import java.util.List;

/**
 * 应用保存请求（与前端表单数据结构完全对齐）
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@Setter
public class ApplicationSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private String provider;

    private String logoUrl;

    private List<String> integrationModes;

    private String description;

    private ApplicationPageConfig page;

    private ApplicationApiClientConfig apiClient;

    private ApplicationApiServerConfig apiServer;

    private ApplicationSsoConfig sso;

}
