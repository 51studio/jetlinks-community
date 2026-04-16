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
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * 处理已提交响应上的 {@link UnsupportedOperationException}。
 *
 * <p>在 Spring Framework 6.2.x + Netty 环境下，当 {@code ServerHttpResponse}
 * 已经 committed 后，其 {@code HttpHeaders} 会变为 {@code ReadOnlyHttpHeaders}。
 * 此时如果 {@code EncoderHttpMessageWriter} 等组件尝试调用
 * {@code headers.setContentLength(...)}，就会抛出 {@code UnsupportedOperationException}。</p>
 *
 * <p>该处理器在异常链的最前端捕获此类场景：一旦响应已经提交，任何 headers/body
 * 的修改都已不可能成功，因此直接静默完成请求，避免：</p>
 * <ul>
 *   <li>{@code CommonErrorControllerAdvice} 再次向已提交响应写入错误体而失败</li>
 *   <li>{@code HttpWebHandlerAdapter} 打印 ERROR 日志刷屏</li>
 *   <li>二次异常污染日志</li>
 * </ul>
 *
 * @author JetLinks
 * @since 2.11.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommittedResponseExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 检查是否是 UnsupportedOperationException 且响应已提交
        if (ex instanceof UnsupportedOperationException && exchange.getResponse().isCommitted()) {
            if (log.isDebugEnabled()) {
                log.debug("Suppressing UnsupportedOperationException for already committed response: {} {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath());
            }
            return Mono.empty();
        }
        
        // 检查异常链中是否包含 UnsupportedOperationException 且响应已提交
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof UnsupportedOperationException && exchange.getResponse().isCommitted()) {
                if (log.isDebugEnabled()) {
                    log.debug("Suppressing UnsupportedOperationException (in cause chain) for already committed response: {} {}",
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getPath());
                }
                return Mono.empty();
            }
            cause = cause.getCause();
        }
        
        // 其他异常继续传播
        return Mono.error(ex);
    }
}
