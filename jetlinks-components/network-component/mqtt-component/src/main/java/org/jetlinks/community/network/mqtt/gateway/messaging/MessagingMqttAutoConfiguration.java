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
package org.jetlinks.community.network.mqtt.gateway.messaging;

import io.vertx.core.Vertx;
import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.community.network.security.CertificateManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MQTT平台消息订阅自动配置
 *
 * @author zhouhao
 * @since 2.11
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "messaging.mqtt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessagingMqttProperties.class)
@ConditionalOnBean({EventBus.class, Vertx.class})
public class MessagingMqttAutoConfiguration {

    @Bean(initMethod = "start", destroyMethod = "destroy")
    public MessagingMqttBroker messagingMqttBroker(Vertx vertx,
                                                    EventBus eventBus,
                                                    MessagingMqttProperties properties,
                                                    UserTokenManager userTokenManager,
                                                    ReactiveAuthenticationManager authenticationManager,
                                                    CertificateManager certificateManager) {
        return new MessagingMqttBroker(
            vertx,
            eventBus,
            properties,
            userTokenManager,
            authenticationManager,
            certificateManager
        );
    }
}
