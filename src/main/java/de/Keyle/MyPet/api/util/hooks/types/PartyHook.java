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

import java.util.List;

/**
 * 此接口表示该 Hook 负责处理队伍检查
 */
public interface PartyHook extends PluginHook {
    /**
     * 返回玩家是否处于某个队伍中 (Ancient, mcMMO, Heroes)
     *
     * @param player 被检查的玩家
     * @return 玩家是否处于某个队伍中
     */
    boolean isInParty(Player player);

    /**
     * 若玩家处于某个队伍中，则返回该队伍的所有成员 (Ancient, mcMMO, Heroes)
     * @param player 队伍成员
     * @return 该队伍的所有成员
     */
    List<Player> getPartyMembers(Player player);
}