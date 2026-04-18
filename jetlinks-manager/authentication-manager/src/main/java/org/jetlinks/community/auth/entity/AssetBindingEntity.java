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
import org.hswebframework.ezorm.rdb.mapping.annotation.JsonCodec;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.JDBCType;
import java.util.List;

/**
 * 资产绑定关联表
 * <p>
 * 存储资产(设备/产品)与目标(组织)之间的绑定关系及权限信息
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "s_asset_binding", indexes = {
    @Index(name = "idx_asset_binding_asset", columnList = "asset_type,asset_id"),
    @Index(name = "idx_asset_binding_target", columnList = "asset_type,target_type,target_id"),
    @Index(name = "idx_asset_binding_unique", columnList = "asset_type,asset_id,target_type,target_id", unique = true)
})
@Comment("资产绑定关联表")
@EnableEntityEvent
public class AssetBindingEntity extends GenericEntity<String> {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    public String getId() {
        return super.getId();
    }

    @Schema(description = "资产类型(device/product)")
    @Column(name = "asset_type", nullable = false, length = 32, updatable = false)
    private String assetType;

    @Schema(description = "资产ID")
    @Column(name = "asset_id", nullable = false, length = 64, updatable = false)
    private String assetId;

    @Schema(description = "目标类型(org)")
    @Column(name = "target_type", nullable = false, length = 32, updatable = false)
    private String targetType;

    @Schema(description = "目标ID(组织ID)")
    @Column(name = "target_id", nullable = false, length = 64, updatable = false)
    private String targetId;

    @Schema(description = "授予的权限列表")
    @Column(name = "permission")
    @JsonCodec
    @ColumnType(jdbcType = JDBCType.CLOB, javaType = String.class)
    private List<String> permission;

    @Schema(description = "创建者ID", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(name = "creator_id", length = 64, updatable = false)
    private String creatorId;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    @Column(name = "create_time", updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    private Long createTime;
}
