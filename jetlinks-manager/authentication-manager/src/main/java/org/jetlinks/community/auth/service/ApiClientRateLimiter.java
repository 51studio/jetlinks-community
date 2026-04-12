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
package org.jetlinks.community.auth.service;

import lombok.AllArgsConstructor;
import org.hswebframework.web.exception.BusinessException;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * API 客户端调用频率限制器（基于 Redis 固定窗口）
 * <p>
 * Key: {@code api_client:rate:{clientId}:{windowMinute}}，窗口 60s，
 * 超过 rateLimit 次时抛出 {@code error.api_client_rate_limit_exceeded}。
 * </p>
 *
 * @author jetlinks
 * @since 2.3
 */
@Service
@AllArgsConstructor
public class ApiClientRateLimiter {

    private static final String RATE_KEY_PREFIX = "api_client:rate:";
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final ReactiveRedisOperations<Object, Object> redis;

    /**
     * 检查并递增计数；若超过限制则返回错误信号
     *
     * @param clientId  客户端 ID
     * @param rateLimit 每分钟最大请求次数，0 表示不限制
     * @return 通过返回 empty；超限返回错误
     */
    public Mono<Void> checkAndIncrement(String clientId, int rateLimit) {
        if (rateLimit <= 0) {
            return Mono.empty();
        }
        long windowMinute = System.currentTimeMillis() / 60_000L;
        String key = RATE_KEY_PREFIX + clientId + ":" + windowMinute;

        return redis
            .opsForValue()
            .increment(key)
            .flatMap(count -> {
                if (count == 1) {
                    // 第一次写入，设置过期时间
                    return redis
                        .expire(key, WINDOW)
                        .then(checkLimit(count, rateLimit));
                }
                return checkLimit(count, rateLimit);
            });
    }

    private Mono<Void> checkLimit(long count, int rateLimit) {
        if (count > rateLimit) {
            return Mono.error(() -> new BusinessException("error.api_client_rate_limit_exceeded", 429));
        }
        return Mono.empty();
    }

}
