/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2026 Keyle
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

package de.Keyle.MyPet.skill.skills;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.MyPetBukkitEntity;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/**
 * 判断某个技能是否可以对宠物并未直接击中的实体造成伤害：例如目标周围的
 * 范围伤害，或命中之后才结算的持续伤害。这类伤害绕过了普通宠物攻击所经过的
 * 检查，因此这里会调用与 Stomp 相同的保护钩子（PvP 设置、区域、领地声明）。
 */
final class SkillDamageCheck {

    private SkillDamageCheck() {
    }

    static boolean canDamage(MyPet myPet, LivingEntity target) {
        Player owner = myPet.getOwner().getPlayer();
        if (owner == null || target == null || target.isDead()) {
            return false;
        }
        if (target == owner) {
            return false;
        }
        if (myPet.getEntity().isPresent() && myPet.getEntity().get() == target) {
            return false;
        }

        if (target instanceof Player) {
            return MyPetApi.getHookHelper().canHurt(owner, (Player) target, true);
        }
        if (target instanceof MyPetBukkitEntity) {
            MyPet targetPet = ((MyPetBukkitEntity) target).getMyPet();
            Player targetOwner = targetPet.getOwner().getPlayer();
            if (targetOwner == null || targetOwner == owner) {
                return false;
            }
            if (!MyPetApi.getHookHelper().canHurt(owner, targetOwner, true)) {
                return false;
            }
        } else if (target instanceof Tameable) {
            Tameable tameable = (Tameable) target;
            AnimalTamer tamer = tameable.isTamed() ? tameable.getOwner() : null;
            if (tamer != null && myPet.getOwner().equals(tamer)) {
                return false;
            }
        }
        return MyPetApi.getHookHelper().canHurt(owner, target);
    }
}
