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
package org.jetlinks.community.configure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 在响应写入阶段拦截 {@link UnsupportedOperationException}（来自 {@code ReadOnlyHttpHeaders}），
 * 避免异常传播到 {@code @RestControllerAdvice} 层被错误日志污染。
 *
 * <p>在 Spring WebFlux 中，{@code DispatcherHandler} 对异常的优先级为：
 * <ol>
 *   <li>{@code @ExceptionHandler}（{@code @RestControllerAdvice}）</li>
 *   <li>{@code WebExceptionHandler}</li>
 * </ol>
 * 因此 {@link CommittedResponseExceptionHandler}（WebExceptionHandler）在
 * {@code CommonErrorControllerAdvice} 之后才能被调用，导致该异常仍被 WARN 级别打印。</p>
 *
 * <p>本 Filter 在 {@code WebFilter} 层（优先级最高）直接装饰响应对象的
 * {@code writeWith()} 方法，从源头拦截异常，避免其进入异常处理链路。</p>
 *
 * @author JetLinks
 * @since 2.11.0
 * @see CommittedResponseExceptionHandler
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReadOnlyHeadersResponseFilter implements WebFilter {

    private static final String READ_ONLY_HEADERS_CLASS = "org.springframework.http.ReadOnlyHttpHeaders";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                return super.writeWith(body)
                    .onErrorResume(UnsupportedOperationException.class, err -> {
                        if (isReadOnlyHeadersException(err)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Suppressing ReadOnlyHttpHeaders exception at response write level: {} {}",
                                    exchange.getRequest().getMethod(),
                                    exchange.getRequest().getPath());
                            }
                            return Mono.empty();
                        }
                        return Mono.error(err);
                    });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    static boolean isReadOnlyHeadersException(Throwable ex) {
        StackTraceElement[] stackTrace = ex.getStackTrace();
        if (stackTrace.length > 0) {
            return READ_ONLY_HEADERS_CLASS.equals(stackTrace[0].getClassName());
        }
        return false;
    }
}
