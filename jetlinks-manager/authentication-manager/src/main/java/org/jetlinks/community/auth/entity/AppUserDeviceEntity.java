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
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.ezorm.rdb.mapping.annotation.EnumCodec;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.hswebframework.web.validator.CreateGroup;
import org.jetlinks.community.auth.enums.AppUserDeviceRelationType;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * C 端用户-设备关联表
 *
 * @author jetlinks
 * @since 2.3
 */
@Getter
@Setter
@Table(name = "s_app_user_device", indexes = {
    @Index(name = "idx_app_user_device_uid", columnList = "user_id"),
    @Index(name = "idx_app_user_device_did", columnList = "device_id"),
    @Index(name = "idx_app_user_device_uid_did", columnList = "user_id,device_id", unique = true)
})
@Comment("C端用户设备绑定关联表")
@EnableEntityEvent
public class AppUserDeviceEntity extends GenericEntity<String> {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    public String getId() {
        return super.getId();
    }

    @Schema(description = "C 端用户 ID")
    @Column(name = "user_id", nullable = false, length = 64, updatable = false)
    @NotBlank(message = "用户ID不能为空", groups = CreateGroup.class)
    private String userId;

    @Schema(description = "设备 ID（关联 dev_device_instance.id）")
    @Column(name = "device_id", nullable = false, length = 64, updatable = false)
    @NotBlank(message = "设备ID不能为空", groups = CreateGroup.class)
    private String deviceId;

    @Schema(description = "设备名称（冗余存储）")
    @Column(name = "device_name", length = 256)
    private String deviceName;

    @Schema(description = "产品 ID（冗余存储）")
    @Column(name = "product_id", length = 64)
    private String productId;

    @Schema(description = "绑定时间", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(name = "bind_time", updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    private Long bindTime;

    @Schema(description = "备注")
    @Column(length = 2000)
    private String description;

    @Schema(description = "关联类型：manage=管理、bind=绑定、share=分享",
            allowableValues = {"manage", "bind", "share"})
    @Column(name = "relation_type", length = 16)
    @DefaultValue("bind")
    @EnumCodec
    @ColumnType(javaType = String.class)
    private AppUserDeviceRelationType relationType;

    @Schema(description = "操作人用户ID（绑定时为绑定人，分享时为分享人，管理类型为空）")
    @Column(name = "operator_id", length = 64)
    private String operatorId;

}
