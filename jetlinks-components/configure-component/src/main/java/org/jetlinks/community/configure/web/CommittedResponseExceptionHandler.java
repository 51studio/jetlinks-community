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

    private static final String READ_ONLY_HEADERS_CLASS = "org.springframework.http.ReadOnlyHttpHeaders";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 快速路径：检查是否是 ReadOnlyHttpHeaders 异常且响应已提交
        if (isReadOnlyHeadersException(ex) && exchange.getResponse().isCommitted()) {
            logSuppressed(exchange);
            return Mono.empty();
        }

        // 检查异常链中是否包含 ReadOnlyHttpHeaders 异常
        // 注意：不再依赖 isCommitted() 作为必须条件，因为在 Netty 异步写入场景下，
        // headers 可能已变为只读但 isCommitted() 仍返回 false
        Throwable cause = ex;
        while (cause != null) {
            if (isReadOnlyHeadersException(cause)) {
                logSuppressed(exchange);
                return Mono.empty();
            }
            cause = cause.getCause();
        }

        // 其他异常继续传播
        return Mono.error(ex);
    }

    /**
     * 判断异常是否由 ReadOnlyHttpHeaders.set() 引发。
     * 通过检查堆栈中最顶层的调用类名来判断，避免对 Spring 内部类的硬依赖。
     */
    private boolean isReadOnlyHeadersException(Throwable ex) {
        if (!(ex instanceof UnsupportedOperationException)) {
            return false;
        }
        StackTraceElement[] stackTrace = ex.getStackTrace();
        if (stackTrace.length > 0) {
            return READ_ONLY_HEADERS_CLASS.equals(stackTrace[0].getClassName());
        }
        return false;
    }

    private void logSuppressed(ServerWebExchange exchange) {
        if (log.isDebugEnabled()) {
            log.debug("Suppressing ReadOnlyHttpHeaders UnsupportedOperationException for response: {} {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath());
        }
    }
}
