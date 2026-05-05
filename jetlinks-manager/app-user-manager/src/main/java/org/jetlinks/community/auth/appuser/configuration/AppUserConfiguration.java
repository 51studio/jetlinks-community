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
package org.jetlinks.community.auth.appuser.configuration;

import org.hswebframework.web.authorization.token.UserTokenManager;
import org.jetlinks.community.auth.appuser.service.AppUserService;
import org.jetlinks.community.auth.service.ApplicationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * 第三方用户模块自动配置
 *
 * @author jetlinks
 * @since 2.3
 */
@AutoConfiguration
public class AppUserConfiguration {

    /**
     * 注册 第三方用户认证过滤器
     */
    @Bean
    @Order(AppUserAuthFilter.ORDER)
    public AppUserAuthFilter appUserAuthFilter(AppUserService appUserService,
                                               UserTokenManager userTokenManager,
                                               ApplicationService applicationService) {
        return new AppUserAuthFilter(appUserService, userTokenManager, applicationService);
    }

}
