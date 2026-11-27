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

package de.Keyle.MyPet.entity.leashing;

import de.Keyle.MyPet.api.entity.leashing.LeashFlag;
import de.Keyle.MyPet.api.entity.leashing.LeashFlagName;
import de.Keyle.MyPet.api.util.configuration.settings.Settings;
import de.Keyle.MyPet.api.util.locale.Translation;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 用于只能通过非伤害方式捕捉的实体的捕捉条件。
 * 专为与嘎枝之心绑定的嘎枝设计：必须通过摧毁其嘎枝之心方块来捕捉，
 * 而不是通过战斗伤害。
 *
 * 伤害为 0 时（表示基于嘎枝之心的捕捉）该条件通过，
 * 伤害大于 0 时（表示基于战斗的捕捉）则不通过。
 */
@LeashFlagName("HeartLinked")
public class HeartLinkedFlag implements LeashFlag {

    @Override
    public boolean check(Player player, LivingEntity entity, double damage, Settings settings) {
        // 伤害为 0 时通过（摧毁之心捕捉）
        // 伤害 > 0 时不通过（基于战斗的捕捉尝试）
        return damage == 0;
    }

    @Override
    public String getMissingMessage(Player player, LivingEntity entity, double damage, Settings settings) {
        return Translation.getString("Message.Command.CaptureHelper.Requirement.HeartLinked", player);
    }
}
