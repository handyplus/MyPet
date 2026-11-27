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

package de.Keyle.MyPet.api.util.service;

import de.Keyle.MyPet.api.Util;
import org.bukkit.event.Listener;

/**
 * 服务是一种注册可用功能的方式
 */
public interface ServiceContainer extends Listener {
    /**
     * 启用该服务，并返回激活是否成功
     *
     * @return 激活是否成功
     */
    default boolean onEnable() {
        return true;
    }

    /**
     * 禁用该服务
     */
    default void onDisable() {
    }

    /**
     * 返回服务的名称
     *
     * @return 服务的名称
     */
    default String getServiceName() {
        ServiceName sn = Util.getClassAnnotation(this.getClass(), ServiceName.class);
        if (sn != null) {
            return sn.value();
        }
        return getClass().getName();
    }
}