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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按资产绑定查询设备/产品.
 * <p>
 * 查询绑定到指定组织的设备/产品:
 * <pre>{@code
 *     "terms":[
 *         {
 *             "column":"id$dim-assets",
 *             "value":{
 *                 "assetType":"device",
 *                 "targets":[{"type":"org","id":"orgId"}]
 *             }
 *         }
 *     ]
 * }</pre>
 *
 * 排除已绑定到指定组织的设备/产品:
 * <pre>{@code
 *     "terms":[
 *         {
 *             "column":"id$dim-assets$not",
 *             "value":{
 *                 "assetType":"device",
 *                 "targets":[{"type":"org","id":"orgId"}]
 *             }
 *         }
 *     ]
 * }</pre>
 *
 * @author jetlinks
 * @since 2.11
 */
@Component
public class DimAssetsTermBuilder extends AbstractTermFragmentBuilder {

    public static final String termType = "dim-assets";

    public DimAssetsTermBuilder() {
        super(termType, "按资产绑定查询");
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
        List<Map<String, String>> targets = (List<Map<String, String>>) valueMap.get("targets");

        if (assetType == null || CollectionUtils.isEmpty(targets)) {
            return EmptySqlFragments.INSTANCE;
        }

        BatchSqlFragments fragments = new BatchSqlFragments();
        List<String> options = term.getOptions();

        if (options.contains("not")) {
            fragments.add(SqlFragments.NOT);
        }

        fragments.addSql("exists(select 1 from",
                         getTableName("s_asset_binding", column),
                         "_ab where _ab.asset_id =", columnFullName,
                         "and _ab.asset_type = ?");
        fragments.addParameter(assetType);

        if (targets.size() == 1) {
            Map<String, String> target = targets.get(0);
            fragments.addSql("and _ab.target_type = ? and _ab.target_id = ?");
            fragments.addParameter(target.get("type"));
            fragments.addParameter(target.get("id"));
        } else {
            fragments.addSql("and (");
            for (int i = 0; i < targets.size(); i++) {
                if (i > 0) {
                    fragments.addSql("or");
                }
                Map<String, String> target = targets.get(i);
                fragments.addSql("(_ab.target_type = ? and _ab.target_id = ?)");
                fragments.addParameter(target.get("type"));
                fragments.addParameter(target.get("id"));
            }
            fragments.add(SqlFragments.RIGHT_BRACKET);
        }

        fragments.add(SqlFragments.RIGHT_BRACKET);
        return fragments;
    }
}
