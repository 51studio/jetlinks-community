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

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.hswebframework.web.authorization.token.UserToken;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.event.TopicPayload;
import org.jetlinks.community.network.security.CertificateManager;
import org.jetlinks.community.network.security.VertxKeyCertTrustOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT平台消息订阅Broker,提供通过MQTT协议订阅平台内部消息的能力.
 *
 * <pre>
 * 架构说明:
 * 1. 启动独立的MQTT服务(messaging.mqtt.port,默认11883)
 * 2. 外部系统通过MQTT客户端连接并订阅Topic
 * 3. 将MQTT订阅映射为EventBus订阅,实现消息桥接
 * 4. 支持通配符订阅(+,#),自动转换JetLinks EventBus通配符(*,**)
 *
 * 认证方式:
 * MQTT客户端通过clientId传递用户访问令牌(token),与企业版行为一致.
 * username和password可以为空.
 * </pre>
 *
 * @author zhouhao
 * @since 2.11
 * @see EventBus
 * @see MessagingMqttProperties
 */
@Slf4j
public class MessagingMqttBroker implements DisposableBean {

    private final Vertx vertx;
    private final EventBus eventBus;
    private final MessagingMqttProperties properties;
    private final UserTokenManager userTokenManager;
    private final ReactiveAuthenticationManager authenticationManager;
    private final CertificateManager certificateManager;

    @Getter
    private final List<io.vertx.mqtt.MqttServer> servers = new ArrayList<>();

    /**
     * 活跃的客户端连接: clientId -> ClientConnection
     */
    private final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();

    public MessagingMqttBroker(Vertx vertx,
                               EventBus eventBus,
                               MessagingMqttProperties properties,
                               UserTokenManager userTokenManager,
                               ReactiveAuthenticationManager authenticationManager,
                               CertificateManager certificateManager) {
        this.vertx = vertx;
        this.eventBus = eventBus;
        this.properties = properties;
        this.userTokenManager = userTokenManager;
        this.authenticationManager = authenticationManager;
        this.certificateManager = certificateManager;
    }

    /**
     * 启动MQTT Broker
     */
    public void start() {
        int instanceCount = Math.max(1, properties.getInstance());
        log.info("MQTT消息订阅Broker正在启动, host:{}, port:{}, instanceCount:{}",
            properties.getHost(), properties.getPort(), instanceCount);
        for (int i = 0; i < instanceCount; i++) {
            final int idx = i;
            createMqttServer()
                .doOnSuccess(server -> {
                    synchronized (servers) {
                        servers.add(server);
                    }
                })
                .subscribe(
                    success -> {},
                    error -> log.error("MQTT消息订阅Broker实例[{}]启动失败", idx, error)
                );
        }
    }

    private Mono<io.vertx.mqtt.MqttServer> createMqttServer() {
        MqttServerOptions options = new MqttServerOptions();
        options.setPort(properties.getPort());
        options.setHost(properties.getHost());
        options.setMaxMessageSize(properties.getMaxMessageSize());
        options.setTcpKeepAlive(true);

        if (properties.isSecure()) {
            options.setSsl(true);
            return certificateManager
                .getCertificate(properties.getCertId())
                .map(VertxKeyCertTrustOptions::new)
                .doOnNext(options::setKeyCertOptions)
                .doOnNext(options::setTrustOptions)
                .flatMap(ignored -> doCreateMqttServer(options));
        }
        return doCreateMqttServer(options);
    }

    private Mono<io.vertx.mqtt.MqttServer> doCreateMqttServer(MqttServerOptions options) {
        io.vertx.mqtt.MqttServer server = io.vertx.mqtt.MqttServer.create(vertx, options);

        server.exceptionHandler(error ->
            log.error("MQTT消息订阅Broker异常", error));

        server.endpointHandler(this::handleEndpoint);

        return Mono.<io.vertx.mqtt.MqttServer>create(sink ->
            server.listen(result -> {
                if (result.succeeded()) {
                    log.info("MQTT消息订阅Broker启动成功,端口:{}", result.result().actualPort());
                    sink.success(server);
                } else {
                    log.error("MQTT消息订阅Broker启动失败", result.cause());
                    sink.error(result.cause());
                }
            }));
    }

    /**
     * 处理新的MQTT客户端连接
     */
    private void handleEndpoint(MqttEndpoint endpoint) {
        String clientId = endpoint.clientIdentifier();

        // 设置断开连接处理器
        endpoint.disconnectHandler(v -> {
            ClientConnection removed = connections.remove(clientId);
            if (removed != null) {
                removed.dispose();
                log.debug("MQTT客户端断开连接, clientId:{}", clientId);
            }
        });

        // 设置关闭处理器
        endpoint.closeHandler(v -> {
            ClientConnection removed = connections.remove(clientId);
            if (removed != null) {
                removed.dispose();
                log.debug("MQTT客户端关闭连接, clientId:{}", clientId);
            }
        });

        // 认证并接受连接
        authenticateAndAccept(endpoint, clientId);
    }

    /**
     * 认证MQTT客户端,认证通过后建立ClientConnection
     * <p>
     * 认证流程:
     * 1. 使用MQTT的clientId作为访问令牌(token)
     * 2. 通过UserTokenManager验证token有效性
     * 3. 获取对应的用户认证信息用于后续权限控制
     * <p>
     * 与企业版一致: token作为clientId, username和password为空即可连接
     */
    private void authenticateAndAccept(MqttEndpoint endpoint, String clientId) {
        // clientId即为访问令牌,与企业版行为一致
        String token = clientId;

        if (!StringUtils.hasText(token)) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            log.warn("MQTT客户端未提供认证令牌(clientId),拒绝连接");
            return;
        }

        // 使用clientId作为令牌进行认证
        userTokenManager
            .getByToken(token)
            .map(UserToken::getUserId)
            .flatMap(authenticationManager::getByUserId)
            .switchIfEmpty(Mono.defer(() -> {
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                log.warn("MQTT客户端令牌无效(clientId):{}", clientId);
                return Mono.empty();
            }))
            .flatMap(auth -> {
                ClientConnection connection = new ClientConnection(clientId, endpoint, auth);
                ClientConnection existing = connections.putIfAbsent(clientId, connection);
                if (existing != null) {
                    endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
                    return Mono.empty();
                }
                connection.init();
                endpoint.accept(false);
                log.info("MQTT客户端认证成功, clientId(即token):{}, userId:{}", clientId, auth.getUser().getId());
                return Mono.empty();
            })
            .onErrorResume(err -> {
                log.warn("MQTT客户端认证失败, clientId(即token):{}, error:{}", clientId, err.getMessage());
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                return Mono.empty();
            })
            .subscribe();
    }

    /**
     * 将MQTT订阅Topic转换为JetLinks EventBus的Topic模式
     * MQTT: + → JetLinks: *
     * MQTT: # → JetLinks: **
     */
    private static String toEventBusTopic(String mqttTopic) {
        if (mqttTopic.contains("#") || mqttTopic.contains("+")) {
            return mqttTopic.replace("#", "**").replace("+", "*");
        }
        return mqttTopic;
    }

    /**
     * 将JetLinks EventBus的Topic模式转换为MQTT订阅Topic
     * MQTT: ** → JetLinks: #
     * MQTT: * → JetLinks: +
     */
    private static String toMqttTopic(String eventBusTopic) {
        if (eventBusTopic.contains("*")) {
            return eventBusTopic.replace("**", "#").replace("*", "+");
        }
        return eventBusTopic;
    }

    /**
     * 将EventBus消息序列化为JSON字符串
     */
    private static String toJsonPayload(TopicPayload topicPayload) {
        try {
            Object decoded = topicPayload.decode();
            if (decoded instanceof String) {
                return (String) decoded;
            }
            // 使用fastjson序列化
            return com.alibaba.fastjson.JSON.toJSONString(decoded);
        } catch (Throwable e) {
            return "{\"topic\":\"" + topicPayload.getTopic() + "\",\"error\":\"decode failed\"}";
        }
    }

    @Override
    public void destroy() {
        synchronized (servers) {
            for (io.vertx.mqtt.MqttServer server : servers) {
                try {
                    server.close();
                } catch (Exception e) {
                    log.warn("关闭MQTT消息订阅Broker失败", e);
                }
            }
            servers.clear();
        }
        connections.values().forEach(ClientConnection::dispose);
        connections.clear();
    }

    /**
     * MQTT客户端连接封装,管理与单个MQTT客户端的交互
     */
    class ClientConnection implements Disposable {

        private final String clientId;
        private final MqttEndpoint endpoint;
        private final Authentication auth;
        private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();
        @Getter
        private final String userId;

        ClientConnection(String clientId, MqttEndpoint endpoint, Authentication auth) {
            this.clientId = clientId;
            this.endpoint = endpoint;
            this.auth = auth;
            this.userId = auth.getUser().getId();
        }

        /**
         * 初始化连接的事件处理
         */
        void init() {
            // 处理订阅请求
            endpoint.subscribeHandler(this::handleSubscribe);

            // 处理取消订阅请求
            endpoint.unsubscribeHandler(this::handleUnsubscribe);

            // 处理Ping请求
            endpoint.pingHandler(v -> {
                if (!endpoint.isAutoKeepAlive()) {
                    endpoint.pong();
                }
            });

            // 处理客户端主动发布的消息(可用于双向通信扩展)
            endpoint.publishHandler(msg -> {
                String topic = msg.topicName();
                String payload = msg.payload().toString(StandardCharsets.UTF_8);
                log.debug("收到MQTT客户端[{}]发布消息, topic:{}, payload:{}", userId, topic, payload);
                // 未来可扩展:将客户端发布的消息转发到EventBus
            })
            .publishAcknowledgeHandler(msgId ->
                log.debug("PUBACK mqtt-messaging[{}] message[{}]", clientId, msgId));
        }

        /**
         * 处理MQTT订阅,转换为EventBus订阅
         */
        private void handleSubscribe(MqttSubscribeMessage msg) {
            for (MqttTopicSubscription sub : msg.topicSubscriptions()) {
                String mqttTopic = sub.topicName();
                int qos = sub.qualityOfService().value();

                // 转换为EventBus topic模式
                String eventBusTopic = toEventBusTopic(mqttTopic);
                String subId = "mqtt-messaging:" + userId + ":" + clientId + ":" + mqttTopic;

                // 避免重复订阅
                if (subscriptions.containsKey(mqttTopic)) {
                    log.debug("MQTT客户端[{}]重复订阅同一Topic,已跳过: {}", userId, mqttTopic);
                    continue;
                }

                log.info("MQTT客户端[{}]订阅Topic, mqttTopic:{}, eventBusTopic:{}",
                    userId, mqttTopic, eventBusTopic);
                Disposable eventSub = eventBus
                    .subscribe(
                        Subscription.of(
                            subId,
                            new String[]{eventBusTopic},
                            Subscription.Feature.local,
                            Subscription.Feature.broker
                        ))
                    .publishOn(Schedulers.parallel())
                    .subscribe(
                        topicPayload -> {
                            log.info("响应订阅JetLinks Topic, mqttTopic:{}, eventBusTopic:{}",
                                mqttTopic, topicPayload.getTopic());
                            if (!endpoint.isConnected()) {
                                return;
                            }
                            String jsonPayload = toJsonPayload(topicPayload);
                            Buffer buffer = Buffer.buffer(jsonPayload);
                            endpoint.publish(
                                toMqttTopic(topicPayload.getTopic()),
                                buffer,
                                MqttQoS.valueOf(qos),
                                false,
                                false
                            );
                        },
                        error -> log.error("MQTT消息订阅事件处理异常, topic:{}", mqttTopic, error)
                    );

                subscriptions.put(mqttTopic, eventSub);
                log.debug("MQTT客户端[{}]订阅Topic成功, mqttTopic:{}, eventBusTopic:{}",
                    userId, mqttTopic, eventBusTopic);
            }
        }

        /**
         * 处理MQTT取消订阅,移除对应的EventBus订阅
         */
        private void handleUnsubscribe(MqttUnsubscribeMessage msg) {
            for (String topic : msg.topics()) {
                Disposable sub = subscriptions.remove(topic);
                if (sub != null) {
                    sub.dispose();
                    log.debug("MQTT客户端[{}]取消订阅Topic:{}", userId, topic);
                }
            }
        }

        @Override
        public void dispose() {
            subscriptions.values().forEach(Disposable::dispose);
            subscriptions.clear();
        }

        @Override
        public boolean isDisposed() {
            return !endpoint.isConnected();
        }
    }
}
