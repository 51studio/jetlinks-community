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
 * C 端用户与设备的关联类型
 *
 * <ul>
 *   <li>{@link #manage}  — 管理：设备的所有者/主管理人，operatorId 为空（由系统自动设置）</li>
 *   <li>{@link #bind}    — 绑定：经授权绑定设备的用户，operatorId 记录执行绑定操作的用户 ID</li>
 *   <li>{@link #share}   — 分享：被其他用户分享了该设备，operatorId 记录分享人的用户 ID</li>
 * </ul>
 *
 * @author jetlinks
 * @since 2.3
 */
@Getter
@AllArgsConstructor
public enum AppUserDeviceRelationType implements EnumDict<String> {

    manage("管理"),
    bind("绑定"),
    share("分享");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }

}
