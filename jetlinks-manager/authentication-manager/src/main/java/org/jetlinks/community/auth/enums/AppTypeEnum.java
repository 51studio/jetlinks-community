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
import org.hswebframework.web.dict.EnumDict;

/**
 * 应用类型枚举
 * 1. 单点登录
 * 2. 第三方应用
 *
 * @author jetlinks
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum AppTypeEnum implements EnumDict<Integer> {

    sso(1, "单点登录"),
    thirdParty(2, "第三方应用");

    private final Integer value;
    private final String text;

    public String getText() {
        return text;
    }
}
