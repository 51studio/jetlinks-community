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
import org.hibernate.validator.constraints.Length;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.ezorm.rdb.mapping.annotation.EnumCodec;
import org.hswebframework.ezorm.rdb.mapping.annotation.JsonCodec;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.api.crud.entity.RecordCreationEntity;
import org.hswebframework.web.api.crud.entity.RecordModifierEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.hswebframework.web.validator.CreateGroup;
import org.jetlinks.community.auth.enums.ApiClientState;

import javax.persistence.Column;
import javax.persistence.Index;
import javax.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.sql.JDBCType;
import java.util.List;

/**
 * API 客户端（第三方系统对接）实体
 *
 * @author jetlinks
 * @since 2.3
 */
@Getter
@Setter
@Table(name = "s_api_client", indexes = {
    @Index(name = "idx_api_client_app_id", columnList = "app_id", unique = true)
})
@Comment("API客户端信息表")
@EnableEntityEvent
public class ApiClientEntity extends GenericEntity<String> implements RecordCreationEntity, RecordModifierEntity {

    @Schema(description = "客户端名称")
    @Column(nullable = false, length = 128)
    @NotBlank(message = "客户端名称不能为空", groups = CreateGroup.class)
    @Length(max = 128)
    private String name;

    @Schema(description = "AppId（应用标识）", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(name = "app_id", nullable = false, length = 128, updatable = false)
    private String appId;

    @Schema(description = "SecretKey（AES加密存储，仅创建或重置时返回明文）", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(name = "secret_key", length = 256)
    private String secretKey;

    @Schema(description = "说明")
    @Column(length = 2000)
    private String description;

    @Schema(description = "状态。enabled 为正常，disabled 为已禁用")
    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("enabled")
    private ApiClientState state;

    @Schema(description = "允许访问的权限列表")
    @Column
    @JsonCodec
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR, javaType = String.class)
    private List<PermissionInfo> permissions;

    @Schema(description = "IP白名单，多个IP用英文逗号分隔，为空表示不限制")
    @Column(name = "ip_white_list", length = 2000)
    private String ipWhiteList;

    @Schema(description = "每分钟最大调用次数，0 表示不限制")
    @Column(name = "rate_limit")
    @DefaultValue("0")
    private Integer rateLimit;

    @Schema(description = "创建者ID", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(length = 64, updatable = false)
    private String creatorId;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    private Long createTime;

    @Schema(description = "修改者ID", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(length = 64)
    private String modifierId;

    @Schema(description = "修改时间", accessMode = Schema.AccessMode.READ_ONLY)
    @Column
    @DefaultValue(generator = Generators.CURRENT_TIME)
    private Long modifyTime;

}
