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
package org.jetlinks.community.auth.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.ReactiveAuthenticationHolder;
import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.hswebframework.web.authorization.ReactiveAuthenticationSupplier;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.hswebframework.web.authorization.token.redis.RedisUserTokenManager;
import org.hswebframework.web.authorization.token.redis.SimpleUserToken;
import org.jetlinks.community.auth.dimension.UserAuthenticationEventPublisher;
import org.jetlinks.community.auth.enums.UserEntityType;
import org.jetlinks.community.auth.service.ApiClientAccessLogService;
import org.jetlinks.community.auth.service.ApiClientRateLimiter;
import org.jetlinks.community.auth.service.ApiClientService;
import org.jetlinks.community.auth.service.ApiClientTokenService;
import org.jetlinks.community.auth.service.AppUserService;
import org.jetlinks.community.auth.web.WebFluxUserController;
import org.jetlinks.core.event.EventBus;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    MenuProperties.class,
    AuthorizationProperties.class
})
public class CustomAuthenticationConfiguration {

    static final String CONDITION_CLASS_NAME = "org.jetlinks.community.microservice.configuration.CloudServicesConfiguration";

    @Bean
    @Primary
    public WebFluxUserController webFluxUserController() {
        return new WebFluxUserController();
    }

    @Bean
    @ConfigurationProperties(prefix = "hsweb.user-token")
    public UserTokenManager userTokenManager(ReactiveRedisOperations<Object, Object> template,
                                             ApplicationEventPublisher eventPublisher) {
        RedisUserTokenManager userTokenManager = new RedisUserTokenManager(template);
        userTokenManager.setLocalCache(Caffeine
                                           .newBuilder()
                                           .expireAfterAccess(Duration.ofMinutes(10))
                                           .expireAfterWrite(Duration.ofHours(2))
                                           .<String, SimpleUserToken>build()
                                           .asMap());
        userTokenManager.setEventPublisher(eventPublisher);
        return userTokenManager;
    }

    @Bean(destroyMethod = "shutdown")
    public UserAuthenticationEventPublisher userDimensionEventPublisher(EventBus eventBus) {
        return new UserAuthenticationEventPublisher(eventBus);
    }

    @Bean
    public AuthorizationPermissionInitializeService authorizationPermissionInitializeService(AuthorizationProperties properties){
        return new AuthorizationPermissionInitializeService(properties);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderAuthCustomizer() {
        return builder -> {
            builder.deserializerByType(UserEntityType.class, new UserEntityTypeJSONDeserializer());
        };
    }

    /**
     * 注册 第三方用户认证过滤器
     */
    @Bean
    @Order(AppUserAuthFilter.ORDER)
    public AppUserAuthFilter appUserAuthFilter(AppUserService appUserService,
                                               UserTokenManager userTokenManager,
                                               ApiClientService apiClientService) {
        return new AppUserAuthFilter(appUserService, userTokenManager, apiClientService);
    }

    /**
     * 注册 API 客户端认证过滤器
     */
    @Bean
    @Order(ApiClientAuthFilter.ORDER)
    public ApiClientAuthFilter apiClientAuthFilter(ApiClientService apiClientService,
                                                   ApiClientTokenService apiClientTokenService,
                                                   ApiClientRateLimiter apiClientRateLimiter,
                                                   ApiClientAccessLogService accessLogService,
                                                   ReactiveAuthenticationManager reactiveAuthenticationManager,
                                                   UserTokenManager userTokenManager) {
        return new ApiClientAuthFilter(apiClientService,
                                       apiClientTokenService,
                                       apiClientRateLimiter,
                                       accessLogService,
                                       reactiveAuthenticationManager,
                                       userTokenManager);
    }

    /**
     * 注册从 ReactorContext 读取 API 客户端 Authentication 的 Supplier
     * 在应用启动时通过 static 初始化块注册，确保与 UserToken 体系并行工作
     */
    @Bean
    public ApiClientReactiveAuthSupplierRegistrar apiClientAuthSupplierRegistrar() {
        ReactiveAuthenticationHolder.addSupplier(new ReactiveAuthenticationSupplier() {
            @Override
            public Mono<Authentication> get(String userId) {
                return Mono.empty();
            }

            @Override
            public Mono<Authentication> get() {
                return Mono.deferContextual(ctx -> Mono
                    .justOrEmpty(ctx.getOrEmpty(Authentication.class)));
            }
        });
        return new ApiClientReactiveAuthSupplierRegistrar();
    }

    /**
     * 标记类，仅用于触发 Supplier 注册，无实际逻辑
     */
    static class ApiClientReactiveAuthSupplierRegistrar {
    }

}
