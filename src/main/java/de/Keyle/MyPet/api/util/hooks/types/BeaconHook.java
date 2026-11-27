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

package de.Keyle.MyPet.api.util.hooks.types;

import de.Keyle.MyPet.api.util.hooks.PluginHook;
import org.bukkit.Location;

/**
 * 该接口定义用于控制各区域内信标技能行为的钩子
 */
public interface BeaconHook extends PluginHook {

    /**
     * 返回在指定位置能否激活信标效果
     *
     * @param location 要检查的位置
     * @return 该位置是否允许使用信标
     */
    boolean isBeaconAllowed(Location location);

    /**
     * 返回在指定位置能否将信标效果共享给非所有者
     *
     * @param location 要检查的位置
     * @return 该位置是否允许共享信标
     */
    boolean isBeaconShareAllowed(Location location);

    /**
     * 返回在指定位置所有者能否获得自己的信标效果
     *
     * @param location 要检查的位置
     * @return 该位置是否允许信标自身效果
     */
    boolean isBeaconSelfAllowed(Location location);

    /**
     * 返回指定位置信标的范围倍数
     *
     * @param location 要检查的位置
     * @return 范围倍数（默认 1.0）
     */
    double getBeaconRangeMultiplier(Location location);

    /**
     * 返回指定位置信标效果的持续时间倍数
     *
     * @param location 要检查的位置
     * @return 持续时间倍数（默认 1.0）
     */
    double getBeaconDurationMultiplier(Location location);

    /**
     * 返回指定位置信标效果的等级（amplifier）修正值
     *
     * @param location 要检查的位置
     * @return 等级修正值（默认 0）
     */
    int getBeaconAmplifierModifier(Location location);
}
