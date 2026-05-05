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
package org.jetlinks.community.auth.appuser.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.api.crud.entity.RecordCreationEntity;
import org.hswebframework.web.api.crud.entity.RecordModifierEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.hswebframework.web.validator.CreateGroup;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * 第三方用户实体（独立账号体系）
 *
 * @author jetlinks
 * @since 2.3
 */
@Getter
@Setter
@Table(name = "s_app_user", indexes = {
    @Index(name = "idx_app_user_app_username", columnList = "app_id,username", unique = true),
    @Index(name = "idx_app_user_app_id", columnList = "app_id"),
    @Index(name = "idx_app_user_phone", columnList = "phone"),
    @Index(name = "idx_app_user_email", columnList = "email")
})
@Comment("第三方用户信息表")
@EnableEntityEvent
public class AppUserEntity extends GenericEntity<String> implements RecordCreationEntity, RecordModifierEntity {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    public String getId() {
        return super.getId();
    }

    @Schema(description = "所属第三方应用 ID（必填）")
    @Column(name = "app_id", nullable = false, length = 64, updatable = false)
    @NotBlank(message = "所属第三方应用ID不能为空", groups = CreateGroup.class)
    private String appId;

    @Schema(description = "用户名（登录账号，唯一）")
    @Column(nullable = false, length = 64, updatable = false)
    @NotBlank(message = "用户名不能为空", groups = CreateGroup.class)
    private String username;

    @Schema(description = "密码（BCrypt 加密，写入时传明文）", accessMode = Schema.AccessMode.WRITE_ONLY)
    @Column(length = 256)
    @JsonIgnore
    private String password;

    @Schema(description = "昵称")
    @Column(length = 128)
    private String nickname;

    @Schema(description = "头像 URL")
    @Column(name = "avatar", length = 2048)
    private String avatar;

    @Schema(description = "手机号")
    @Column(length = 32)
    private String phone;

    @Schema(description = "邮箱")
    @Column(length = 256)
    private String email;

    @Schema(description = "状态：1=启用，0=禁用")
    @Column
    @DefaultValue("1")
    private Byte status;

    @Schema(description = "第三方登录类型（预留：wechat/alipay 等）")
    @Column(name = "third_party_type", length = 64)
    private String thirdPartyType;

    @Schema(description = "第三方用户 ID（预留）")
    @Column(name = "third_party_id", length = 256)
    private String thirdPartyId;

    @Schema(description = "用户类型：user-普通用户，application-第三方用户")
    @Column(name = "user_type", length = 32)
    @ColumnType(javaType = String.class)
    @DefaultValue("application")
    private String userType;

    @Schema(description = "创建者 ID", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(length = 64, updatable = false)
    private String creatorId;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    private Long createTime;

    @Schema(description = "修改者 ID", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(length = 64)
    private String modifierId;

    @Schema(description = "修改时间", accessMode = Schema.AccessMode.READ_ONLY)
    @Column
    @DefaultValue(generator = Generators.CURRENT_TIME)
    private Long modifyTime;

}
