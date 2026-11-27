/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2019 Keyle
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

package de.Keyle.MyPet.api.util.hooks;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;

/**
 * 插件钩子用于封装从其他插件使用的功能。
 */
public interface PluginHook extends Listener {

    /**
     * 钩子被启用时调用的方法
     *
     * @return 激活是否成功
     */
    default boolean onEnable() {
        return true;
    }

    /**
     * 钩子被禁用时调用的方法
     */
    default void onDisable() {
    }

    /**
     * 钩子被启用之前调用以加载配置的方法
     */
    default void loadConfig(ConfigurationSection config) {
    }

    /**
     * 返回被钩插件的名称
     *
     * @return 插件名称
     */
    default String getPluginName() {
        if (this.getClass().isAnnotationPresent(PluginHookName.class)) {
            PluginHookName hookNameAnnotation = this.getClass().getAnnotation(PluginHookName.class);
            return hookNameAnnotation.value();
        }
        return "INVALID HOOK";
    }

    default String getActivationMessage() {
        return "";
    }
}