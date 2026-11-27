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

package de.Keyle.MyPet.api.util.service.types;

import de.Keyle.MyPet.api.entity.MyPetType;
import de.Keyle.MyPet.api.gui.IconMenuItem;
import de.Keyle.MyPet.api.util.service.ServiceContainer;
import de.Keyle.MyPet.api.util.service.ServiceName;
import org.bukkit.Material;

@ServiceName("EggIconService")
public class EggIconService implements ServiceContainer {
    /**
     * 用于为宠物类型选择图标 Material 的 API 实现。
     * <p>
     * NMS 专用的子类可以重写该方法来修正刷怪蛋识别。
     */
    public void updateIcon(MyPetType type, IconMenuItem icon) {
        // 清除之前的光效
        icon.setGlowing(false);

        // 尝试根据枚举名解析出类似 "PIGLIN_BRUTE_SPAWN_EGG" 的 Material
        String upperSnake = toUpperSnake(type.name());
        String matName = upperSnake + "_SPAWN_EGG";

        Material material = Material.matchMaterial(matName);

        // 已知的例外情况或没有官方刷怪蛋的实体
        if (material == null) {
            switch (type) {
                case EnderDragon:
                    material = Material.DRAGON_EGG; // 方块蛋
                    break;
                case Snowman:
                    material = Material.PUMPKIN; // 雪傀儡没有刷怪蛋
                    break;
                case Giant:
                    material = Material.ZOMBIE_SPAWN_EGG; // 没有官方刷怪蛋
                    break;
                case Illusioner:
                    material = Material.SQUID_SPAWN_EGG; // 没有官方刷怪蛋
                    icon.setGlowing(true);
                    break;
                case IronGolem:
                    material = Material.SKELETON_SPAWN_EGG; // 1.19.2 及以下版本需要
                    icon.setGlowing(true);
                    break;
                case Wither:
                    material = Material.ENDERMITE_SPAWN_EGG; // 1.19.2 及以下版本需要
                    icon.setGlowing(true);
                    break;
                default:
                    // 显示屏障方块以指出问题
                    material = Material.BARRIER;
                    icon.setGlowing(true);
                    break;
            }
        }

        icon.setMaterial(material);
    }

    /**
     * 辅助方法：把 PascalCase 的枚举名（例如 PiglinBrute）转换为 UPPER_SNAKE（PIGLIN_BRUTE）
     */
    protected static String toUpperSnake(String in) {
        return in.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }
}