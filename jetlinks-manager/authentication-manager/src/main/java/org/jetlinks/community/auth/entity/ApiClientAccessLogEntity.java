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
package org.jetlinks.community.auth.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.crud.generator.Generators;

import javax.persistence.Column;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * API 客户端调用日志实体
 *
 * @author jetlinks
 * @since 2.3
 */
@Getter
@Setter
@Table(name = "s_api_client_access_log", indexes = {
    @Index(name = "idx_api_cal_client_id", columnList = "client_id"),
    @Index(name = "idx_api_cal_request_time", columnList = "request_time")
})
@Comment("API客户端调用日志表")
public class ApiClientAccessLogEntity extends GenericEntity<String> {

    @Schema(description = "客户端ID")
    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Schema(description = "客户端名称")
    @Column(name = "client_name", length = 128)
    private String clientName;

    @Schema(description = "请求路径")
    @Column(name = "request_path", length = 512)
    private String requestPath;

    @Schema(description = "请求方法")
    @Column(name = "request_method", length = 16)
    private String requestMethod;

    @Schema(description = "客户端 IP")
    @Column(name = "ip", length = 64)
    private String ip;

    @Schema(description = "请求时间（毫秒时间戳）")
    @Column(name = "request_time")
    @DefaultValue(generator = Generators.CURRENT_TIME)
    private Long requestTime;

    @Schema(description = "响应状态码")
    @Column(name = "response_status")
    private Integer responseStatus;

}
