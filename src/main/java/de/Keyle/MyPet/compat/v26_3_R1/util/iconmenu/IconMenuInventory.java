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

package de.Keyle.MyPet.compat.v26_3_R1.util.iconmenu;

import de.Keyle.MyPet.api.util.Compat;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Minecraft v26_3_R1 专用的 IconMenuInventory。
 * <p>
 * 本类从 API 层的 {@code IconMenuInventory} 继承全部 GUI 逻辑，
 * 仅重写 {@link #addGlint(ItemMeta)} 以使用原生的现代发光 API。
 * <p>
 * 预期行为：
 * <ul>
 *   <li>不使用 NMS</li>
 *   <li>大部分功能交由共享的父类实现</li>
 *   <li>使用现代的发光覆盖</li>
 * </ul>
 */
@Compat("v26_3_R1")
public class IconMenuInventory extends de.Keyle.MyPet.api.gui.IconMenuInventory {
    /**
     * 在 1.20.4+ 上使用现代服务器 API 应用发光效果，
     * 绕过旧版本使用的虚拟附魔回退方案。
     *
     * @param meta 要修改的 ItemMeta
     * @return 已应用发光标记的 ItemMeta
     */
    @Override
    protected ItemMeta addGlint(ItemMeta meta) {
        // 现代 Spigot/Paper API 调用；在 1.20.4+ 环境中安全
        meta.setEnchantmentGlintOverride(true);
        return meta;
    }
}
