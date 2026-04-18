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
package org.jetlinks.community.auth.service.terms;

import org.apache.commons.collections4.CollectionUtils;
import org.hswebframework.ezorm.core.param.Term;
import org.hswebframework.ezorm.rdb.metadata.RDBColumnMetadata;
import org.hswebframework.ezorm.rdb.operator.builder.fragments.BatchSqlFragments;
import org.hswebframework.ezorm.rdb.operator.builder.fragments.EmptySqlFragments;
import org.hswebframework.ezorm.rdb.operator.builder.fragments.SqlFragments;
import org.hswebframework.ezorm.rdb.operator.builder.fragments.term.AbstractTermFragmentBuilder;
import org.hswebframework.ezorm.rdb.utils.SqlUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按资产维度查询组织.
 * <p>
 * 查询绑定了指定资产的组织:
 * <pre>{@code
 *     "terms":[
 *         {
 *             "column":"id$assets-dim",
 *             "value":{
 *                 "assetType":"device",
 *                 "assetIds":["deviceId1","deviceId2"],
 *                 "dimensionType":"org"
 *             }
 *         }
 *     ]
 * }</pre>
 *
 * @author jetlinks
 * @since 2.11
 */
@Component
public class AssetsDimTermBuilder extends AbstractTermFragmentBuilder {

    public static final String termType = "assets-dim";

    public AssetsDimTermBuilder() {
        super(termType, "按资产维度查询组织");
    }

    @Override
    @SuppressWarnings("unchecked")
    public SqlFragments createFragments(String columnFullName, RDBColumnMetadata column, Term term) {

        Object value = term.getValue();
        if (!(value instanceof Map)) {
            return EmptySqlFragments.INSTANCE;
        }

        Map<String, Object> valueMap = (Map<String, Object>) value;
        String assetType = String.valueOf(valueMap.get("assetType"));
        List<String> assetIds = (List<String>) valueMap.get("assetIds");
        String dimensionType = valueMap.containsKey("dimensionType")
            ? String.valueOf(valueMap.get("dimensionType"))
            : "org";

        if (assetType == null || CollectionUtils.isEmpty(assetIds)) {
            return EmptySqlFragments.INSTANCE;
        }

        BatchSqlFragments fragments = new BatchSqlFragments();
        List<String> options = term.getOptions();

        if (options.contains("not")) {
            fragments.add(SqlFragments.NOT);
        }

        fragments.addSql("exists(select 1 from",
                         getTableName("s_asset_binding", column),
                         "_ab where _ab.target_type = ?");
        fragments.addParameter(dimensionType);

        fragments.addSql("and _ab.target_id =", columnFullName);

        fragments.addSql("and _ab.asset_type = ?");
        fragments.addParameter(assetType);

        fragments.addSql("and _ab.asset_id in(")
                 .add(SqlUtils.createQuestionMarks(assetIds.size()))
                 .add(SqlFragments.RIGHT_BRACKET)
                 .addParameter(assetIds);

        fragments.add(SqlFragments.RIGHT_BRACKET);
        return fragments;
    }
}
