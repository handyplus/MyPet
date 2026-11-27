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

package de.Keyle.MyPet.api.commands;

import org.bukkit.entity.Player;

public interface HelpProvider {

    /** 帮助文本的翻译键（例如 "Message.Command.Help.Call"）。返回 null 表示在帮助中隐藏。 */
    default String getHelpTranslationKey() {
        return null;
    }

    /** 在帮助中显示的命令字符串（例如 "/petcall"）。返回 null 表示在帮助中隐藏。 */
    default String getHelpCommand() {
        return null;
    }

    /** 该命令是否应在帮助中对指定玩家可见。 */
    default boolean isVisibleTo(Player player) {
        return true;
    }

    /** 在帮助列表中的显示顺序（数值越小越靠前）。 */
    default int getHelpOrder() {
        return Integer.MAX_VALUE;
    }

    /** 该命令在帮助列表中所属的分类。 */
    default CommandCategory getHelpCategory() {
        return CommandCategory.PET;
    }

    /** 直接的帮助说明，在未设置翻译键时使用。 */
    default String getHelpDescription() {
        return null;
    }
}
