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
package org.jetlinks.community.auth.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.ezorm.rdb.mapping.annotation.EnumCodec;
import org.hswebframework.web.dict.I18nEnumDict;
import org.hswebframework.web.i18n.LocaleUtils;
import org.jetlinks.community.auth.entity.IntegrationMode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用类型/提供商枚举
 * <p>
 * 对应前端应用管理中的 provider 选项。
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum ApplicationProvider implements I18nEnumDict<String> {

    INTERNAL_STANDALONE("internal-standalone", "独立应用", Arrays.asList(
        new IntegrationMode("page", "页面集成"),
        new IntegrationMode("apiClient", "API客户端"),
        new IntegrationMode("apiServer", "API服务"),
        new IntegrationMode("ssoClient", "单点登录")
    )),

    INTERNAL_INTEGRATED("internal-integrated", "集成应用", Arrays.asList(
        new IntegrationMode("page", "页面集成")
    )),

    THIRD_PARTY("third-party", "第三方应用", Arrays.asList(
        new IntegrationMode("page", "页面集成"),
        new IntegrationMode("apiClient", "API客户端"),
        new IntegrationMode("apiServer", "API服务"),
        new IntegrationMode("ssoClient", "单点登录")
    )),

    WECHAT_WEBAPP("wechat-webapp", "微信网站应用", Arrays.asList(
        new IntegrationMode("ssoClient", "单点登录")
    )),

    WECHAT_MINIAPP("wechat-miniapp", "微信小程序", Arrays.asList(
        new IntegrationMode("ssoClient", "单点登录")
    )),

    DINGTALK_ENT_APP("dingtalk-ent-app", "钉钉企业应用", Arrays.asList(
        new IntegrationMode("ssoClient", "单点登录")
    ));

    private final String provider;
    private final String name;
    private final List<IntegrationMode> integrationModes;

    public String getValue() {
        return provider;
    }

    public String getText() {
        return name;
    }

    /**
     * 根据 provider 值查找枚举
     */
    public static ApplicationProvider of(String provider) {
        if (provider == null) {
            return null;
        }
        for (ApplicationProvider item : values()) {
            if (item.provider.equals(provider)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 获取接入方式的显示文本
     */
    public static String getModeText(String modeValue) {
        if (modeValue == null) {
            return null;
        }
        for (ApplicationProvider item : values()) {
            for (IntegrationMode mode : item.integrationModes) {
                if (modeValue.equals(mode.getValue())) {
                    return mode.getText();
                }
            }
        }
        return modeValue;
    }

    @Override
    public Object getWriteJSONObject() {
        if (isWriteJSONObjectEnabled()) {
            Map<String, Object> jsonObject = new HashMap<>();
            jsonObject.put("provider", provider);
            jsonObject.put("name", name);
            jsonObject.put("integrationModes", integrationModes);
            return jsonObject;
        }
        return name();
    }

}
