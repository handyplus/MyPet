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

package de.Keyle.MyPet.util.translation;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.PlatformHelper;
import de.Keyle.MyPet.api.entity.MyPetType;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import org.bukkit.entity.Player;

/**
 * 解析新建宠物的默认显示名称。
 *
 * <p>Mojang 原版的 {@code entity.minecraft.<bukkit-name>} 翻译——
 * 通过 {@link VanillaTranslationLoader#resolveEntityName(String, String)}
 * 按创建者的客户端语言环境解析——是权威来源。当原版映射表尚未加载时（离线
 * 服务器、CDN 出错、插件启用时的异步竞态），加载器会在内部回退到由
 * {@link MyPetType#getBukkitName()} 机械推导出的英文名称。</p>
 *
 * <p>MyPet 语言文件中的旧版 {@code Name.<PetType>} 条目在此处被
 * 刻意 <strong>不</strong> 使用。Mojang 的翻译覆盖了所有受支持语言环境下的
 * 每一种随游戏发布的实体，因此为每个语言环境单独写 MyPet 覆盖项只会造成
 * 多余的维护负担。想要自定义默认名称的服主和管理员可以在创建后用
 * {@code /petname} 重命名。</p>
 */
public final class PetDefaultNameResolver {

    private PetDefaultNameResolver() {}

    public static String resolve(MyPetType type, MyPetPlayer player) {
        return resolve(type, player == null ? null : player.getLanguage());
    }

    public static String resolve(MyPetType type, Player player) {
        if (player == null) return resolve(type, (String) null);
        // 在不支持的 NMS 版本上，PlatformHelper 在 onLoad 与 onEnable 中
        // setEnabled(false) 这一关卡之间为 null——来自商店/GUI/事件路径的调用方在这个
        // 时间窗口内仍可能到达这里，否则会在语言环境查询内部抛出 NPE。
        PlatformHelper helper = MyPetApi.getPlatformHelper();
        return resolve(type, helper == null ? null : helper.getPlayerLanguage(player));
    }

    public static String resolve(MyPetType type, String localeTag) {
        return VanillaTranslationLoader.resolveEntityName(type.getBukkitName(), localeTag);
    }
}
