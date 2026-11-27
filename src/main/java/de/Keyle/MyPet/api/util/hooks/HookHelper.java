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

package de.Keyle.MyPet.api.util.hooks;

import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.util.hooks.types.EconomyHook;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@link HookHelper} 是本插件从其他插件所需默认功能的钩子封装。
 */
public abstract class HookHelper {

    /**
     * 返回某个玩家能否伤害另一个玩家，以及（如需要）反向是否成立
     *
     * @param attacker  攻击方玩家
     * @param victim    被攻击方玩家
     * @param viceversa 为 true 时同时检查被攻击方能否攻击攻击方
     * @return 攻击方能否伤害被攻击方（以及可选的相反方向）
     */
    public abstract boolean canHurt(Player attacker, Player victim, boolean viceversa);

    /**
     * 返回某个玩家能否伤害另一个玩家。
     *
     * @param attacker 攻击方玩家
     * @param victim   被攻击方玩家
     * @return 攻击方能否伤害被攻击方玩家
     */
    public abstract boolean canHurt(Player attacker, Player victim);

    /**
     * 返回某个玩家能否伤害指定实体。
     *
     * @param attacker 攻击方玩家
     * @param victim   被攻击方实体
     * @return 攻击方能否伤害该实体
     */
    public abstract boolean canHurt(Player attacker, Entity victim);

    /**
     * 返回 MyPet 玩家是否处于任意类型的竞技场中（小游戏、生存游戏、PvP 竞技场等）
     *
     * @param player MyPet 玩家
     * @return 玩家是否在竞技场中
     */
    public abstract boolean isPetAllowed(MyPetPlayer player);

    /**
     * 返回玩家能否在指定位置飞行宠物
     *
     * @param location 要检查的位置
     * @return 玩家能否飞行该宠物
     */
    public abstract boolean canMyPetFlyAt(Location location);

    /**
     * 返回玩家是否处于队伍中（Ancient、mcMMO、Heroes）
     *
     * @param player 要检查的玩家
     * @return 玩家是否在队伍中
     */
    public abstract boolean isInParty(Player player);

    /**
     * 如果玩家处于队伍中，返回该队伍的所有成员（Ancient、mcMMO、Heroes）
     *
     * @param player 队伍成员
     * @return 队伍的所有成员
     */
    public abstract List<Player> getPartyMembers(Player player);

    /**
     * 返回玩家是否处于隐身（vanish）状态
     *
     * @param player 要检查的玩家
     * @return 玩家是否处于隐身状态
     */
    public abstract boolean isVanished(Player player);

    /**
     * 返回 Vault 经济钩子
     *
     * @return Vault 经济钩子
     */
    public abstract EconomyHook getEconomy();

    /**
     * 返回 Vault 经济钩子是否已启用
     *
     * @return 已启用时返回 true
     */
    public abstract boolean isEconomyEnabled();

    // 信标区域检查

    /**
     * 返回在指定位置能否激活信标效果
     *
     * @param location 要检查的位置
     * @return 该位置是否允许使用信标
     */
    public abstract boolean isBeaconAllowed(Location location);

    /**
     * 返回在指定位置能否将信标效果共享给非所有者
     *
     * @param location 要检查的位置
     * @return 该位置是否允许共享信标
     */
    public abstract boolean isBeaconShareAllowed(Location location);

    /**
     * 返回在指定位置所有者能否获得自己的信标效果
     *
     * @param location 要检查的位置
     * @return 该位置是否允许信标自身效果
     */
    public abstract boolean isBeaconSelfAllowed(Location location);

    /**
     * 返回指定位置信标的范围倍数
     *
     * @param location 要检查的位置
     * @return 范围倍数（默认 1.0）
     */
    public abstract double getBeaconRangeMultiplier(Location location);

    /**
     * 返回指定位置信标效果的持续时间倍数
     *
     * @param location 要检查的位置
     * @return 持续时间倍数（默认 1.0）
     */
    public abstract double getBeaconDurationMultiplier(Location location);

    /**
     * 返回指定位置信标效果的等级（amplifier）修正值
     *
     * @param location 要检查的位置
     * @return 等级修正值（默认 0）
     */
    public abstract int getBeaconAmplifierModifier(Location location);
}