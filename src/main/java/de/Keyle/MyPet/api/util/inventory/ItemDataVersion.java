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

package de.Keyle.MyPet.api.util.inventory;

import de.Keyle.MyPet.util.nbt.*;

import java.util.Map;

/**
 * 保存在已保存物品复合标签上的 {@code DataVersion} 标记。
 * <p>
 * 原版物品 NBT 本身不携带版本信息，因此读取方无法判断是哪个 Minecraft
 * 版本写入的，只能在运行数据修复器（data fixers）之前根据复合标签的
 * 结构去猜测。MyPet 因此把服务器的数据版本与物品一起写入，作为顶层
 * int 类型的 {@code DataVersion}（与原版用于区块和实体的键相同），并在
 * 再次加载该物品时把它用作数据修复器的起始版本。MyPet 4 的 MyPet 3
 * 导入器也会读取同一个键。
 * <p>
 * 只有 Minecraft 1.13 或更高版本保存的物品才会被标记；更早的物品按其
 * 结构识别。空气永远不会被标记。
 */
public final class ItemDataVersion {

    public static final String KEY = "DataVersion";

    /** 当物品不带标记时由 {@link #read} 返回。 */
    public static final int NONE = -1;

    private ItemDataVersion() {
    }

    /**
     * 将 {@code dataVersion} 写入一个已保存的非空物品复合标签。
     *
     * @return 同一个复合标签
     */
    public static TagCompound stamp(TagCompound item, int dataVersion) {
        if (item == null || dataVersion <= 0 || isAir(item)) {
            return item;
        }
        item.getCompoundData().put(KEY, new TagInt(dataVersion));
        return item;
    }

    /**
     * @return 存储的数据版本；如果物品没有可用的标记则返回 {@link #NONE}
     */
    public static int read(TagCompound item) {
        if (item == null) {
            return NONE;
        }
        TagBase tag = item.getCompoundData().get(KEY);
        int value;
        if (tag instanceof TagInt) {
            value = ((TagInt) tag).getIntData();
        } else if (tag instanceof TagShort) {
            value = ((TagShort) tag).getShortData();
        } else if (tag instanceof TagByte) {
            value = ((TagByte) tag).getByteData();
        } else if (tag instanceof TagLong) {
            long longValue = ((TagLong) tag).getLongData();
            value = longValue > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) longValue;
        } else {
            return NONE;
        }
        return value >= 0 ? value : NONE;
    }

    /**
     * 数据修复器应使用的起始版本：取已存储的版本，但不会超过当前运行的
     * 服务器版本，因为服务器的修复器无法向后运行。
     */
    public static int startVersion(int stored, int serverDataVersion) {
        return Math.min(stored, serverDataVersion);
    }

    /**
     * @return 该物品的浅拷贝，不含标记，可直接交给原版解析器
     */
    public static TagCompound withoutStamp(TagCompound item) {
        TagCompound copy = new TagCompound();
        for (Map.Entry<String, TagBase> entry : item.getCompoundData().entrySet()) {
            if (!KEY.equals(entry.getKey())) {
                copy.getCompoundData().put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private static boolean isAir(TagCompound item) {
        TagBase id = item.getCompoundData().get("id");
        if (!(id instanceof TagString)) {
            // 1.13 之前的数字 ID 不会走到这里；没有字符串 id 的复合标签并非
            // 编解码器写入的物品，因此保持原样。
            return true;
        }
        String value = ((TagString) id).getStringData();
        return value.isEmpty() || "minecraft:air".equals(value) || "air".equals(value);
    }
}
