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
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.community.auth.entity.ApiClientAccessLogEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * API 客户端调用记录服务
 *
 * @author jetlinks
 * @since 2.3
 */
@Slf4j
@Service
@AllArgsConstructor
public class ApiClientAccessLogService extends GenericReactiveCrudService<ApiClientAccessLogEntity, String> {

    /**
     * 异步记录一次 API 调用
     *
     * @param clientId      客户端 ID
     * @param clientName    客户端名称
     * @param requestPath   请求路径
     * @param requestMethod 请求方法
     * @param ip            客户端 IP
     * @param responseStatus 响应状态码
     */
    public void asyncRecord(String clientId,
                            String clientName,
                            String requestPath,
                            String requestMethod,
                            String ip,
                            int responseStatus) {
        ApiClientAccessLogEntity entity = new ApiClientAccessLogEntity();
        entity.setId(IDGenerator.MD5.generate());
        entity.setClientId(clientId);
        entity.setClientName(clientName);
        entity.setRequestPath(requestPath);
        entity.setRequestMethod(requestMethod);
        entity.setIp(ip);
        entity.setRequestTime(System.currentTimeMillis());
        entity.setResponseStatus(responseStatus);

        this.save(entity)
            .doOnError(e -> log.warn("Failed to save API client access log for clientId={}", clientId, e))
            .subscribe();
    }

    /**
     * 按客户端 ID 分页查询调用记录
     *
     * @param clientId 客户端 ID
     * @param query    分页/排序参数
     * @return 分页结果
     */
    public Mono<PagerResult<ApiClientAccessLogEntity>> queryByClientId(String clientId,
                                                                        QueryParamEntity query) {
        query.toNestQuery(q -> q.and("client_id", clientId));
        return this.queryPager(query);
    }

}
