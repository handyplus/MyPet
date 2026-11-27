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

package de.Keyle.MyPet.api.util.hooks.types;

import de.Keyle.MyPet.api.util.hooks.PluginHook;
import org.bukkit.entity.Player;

/**
 * 此接口表示该 Hook 负责处理队伍检查
 */
public interface PermissionGroupHook extends PluginHook {

    /**
     * 返回玩家是否处于某个权限组中 (Vault compatible)
     *
     * @param player 被检查的玩家
     * @return 玩家是否处于某个权限组中
     */
    boolean isInGroup(Player player, String group);

    /**
     * 返回玩家是否在某个世界中处于某个权限组中 (Vault compatible)
     *
     * @param player 被检查的玩家
     * @param world  要检查的世界
     * @return 玩家是否在该世界中处于某个权限组中
     */
    boolean isInGroup(Player player, String group, String world);
}