/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2020 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.util.hooks;

import de.Keyle.MyPet.MyPetApi;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 保护类 hook 中的伤害检查在出错时按“失败即拒绝”处理：当被挂钩的插件抛出异常时，
 * 不允许宠物伤害目标。每个 hook 只以警告形式记录一次错误，这样服主能看到宠物为何
 * 停止造成伤害，同时避免出问题的 hook 刷屏日志。
 */
final class ProtectionHookErrors {

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private ProtectionHookErrors() {
    }

    static boolean failClosed(Class<?> hook, Throwable t) {
        if (REPORTED.add(hook.getName())) {
            MyPetApi.getLogger().log(Level.WARNING, hook.getSimpleName() + " damage check failed, so Pets are not allowed to hurt targets it protects until it works again. Is the plugin it hooks into up to date?", t);
        }
        return false;
    }
}
