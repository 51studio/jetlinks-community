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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 平台消息订阅配置
 *
 * @author zhouhao
 * @since 2.11
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "messaging.mqtt")
public class MessagingMqttProperties {

    /**
     * 是否启用MQTT平台消息订阅功能
     */
    private boolean enabled = false;

    /**
     * 绑定网卡地址
     */
    private String host = "0.0.0.0";

    /**
     * 监听端口，默认11883（与设备接入MQTT的1883区分）
     */
    private int port = 11883;

    /**
     * 是否启用TSL
     */
    private boolean secure = false;

    /**
     * 证书ID
     */
    private String certId;

    /**
     * 最大消息长度
     */
    private int maxMessageSize = 8096;

    /**
     * MQTT服务实例数量（线程数）
     */
    private int instance = Runtime.getRuntime().availableProcessors();

}
