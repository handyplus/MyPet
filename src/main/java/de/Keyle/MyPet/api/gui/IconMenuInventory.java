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

package de.Keyle.MyPet.api.gui;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认的、与版本无关的 IconMenu 物品栏实现，仅使用 Bukkit API。
 * <p>
 * 该类集中了各兼容模块共用的行为。它不依赖任何 NMS 或 CraftBukkit
 * 类型，应能适用于从旧版服务端到当前 Spigot/Paper 版本。兼容层如有需要可以
 * 继承该类，但大多数版本应该无需覆盖。
 */
public class IconMenuInventory {

    private Inventory bukkitInventory;
    /**
     * 以槽位计的逻辑大小。在调用 {@link #open(IconMenu, HumanEntity)} 时设置。
     */
    @Getter
    private int size;

    /**
     * 为指定玩家打开菜单，并将所有选项渲染到一个全新的 Bukkit 物品栏中。
     * 必须在服务器主线程调用。
     */
    public void open(IconMenu menu, HumanEntity player) {
        this.size = menu.getSize();
        this.bukkitInventory = Bukkit.createInventory(null, size, menu.getTitle());

        for (int slot = 0; slot < size; slot++) {
            IconMenuItem menuItem = menu.getOption(slot);
            if (menuItem != null) {
                this.bukkitInventory.setItem(slot, createItemStack(menuItem));
            }
        }
        player.openInventory(this.bukkitInventory);
    }

    /**
     * 根据提供的模型重新渲染当前已打开物品栏中的物品。
     * 若菜单未打开则不做任何操作。
     */
    public void update(IconMenu menu) {
        if (this.bukkitInventory == null) return;

        for (int slot = 0; slot < size; slot++) {
            IconMenuItem menuItem = menu.getOption(slot);
            if (menuItem != null) {
                this.bukkitInventory.setItem(slot, createItemStack(menuItem));
            } else {
                this.bukkitInventory.setItem(slot, new ItemStack(Material.AIR));
            }
        }
    }

    /**
     * 为所有当前查看者关闭物品栏，并清除底层引用。
     */
    public void close() {
        if (this.bukkitInventory == null) return;

        List<HumanEntity> viewers = new ArrayList<>(getViewers());
        for (HumanEntity viewer : viewers) {
            viewer.closeInventory();
        }
        this.bukkitInventory = null;
    }

    /**
     * 检查给定的 Bukkit 物品栏实例是否与该菜单的物品栏相同。
     */
    public boolean isMenuInventory(Inventory inv) {
        return this.bukkitInventory != null && this.bukkitInventory.equals(inv);
    }

    /**
     * 底层物品栏的当前查看者。未打开时返回空列表。
     */
    public List<HumanEntity> getViewers() {
        if (this.bukkitInventory == null) {
            return Collections.emptyList();
        }
        return this.bukkitInventory.getViewers();
    }

    /**
     * 使用 Bukkit 安全的方式为物品添加视觉发光效果。
     * <p>
     * 现代服务端（1.20.4+）提供 {@code setEnchantmentGlintOverride}。但该 API
     * 在旧版服务端上并不存在，因此默认实现使用一个虚拟附魔并将其隐藏。
     * 子类可以覆盖该方法，在可用时使用现代 API。
     *
     * @param meta 要修改的基础 {@link ItemMeta}
     * @return 应用发光效果后修改过的 {@link ItemMeta}
     */
    protected ItemMeta addGlint(ItemMeta meta) {
        // 兼容旧版的发光实现：添加虚拟附魔并将其从显示中隐藏
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        return meta;
    }

    /**
     * 仅使用 Bukkit API 从菜单项构建适用于 GUI 的 ItemStack。
     */
    protected ItemStack createItemStack(IconMenuItem icon) {
        // 防御性默认值
        ItemStack is = new ItemStack(icon.getMaterial() != null ? icon.getMaterial() : Material.BARRIER,
                Math.max(1, icon.getAmount()));
        is.setItemMeta(icon.getBukkitMeta());
        ItemMeta meta = is.getItemMeta();
        if (meta == null) return is;

        // 标题
        if (icon.getTitle() != null && !icon.getTitle().isEmpty()) {
            meta.setDisplayName(icon.getTitle());
        }

        // 描述文本
        if (!icon.getLore().isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : icon.getLore()) {
                lore.add((line == null || line.isEmpty()) ? " " : line);
            }
            meta.setLore(lore);
        }

        // 发光：若存在 setEnchantmentGlintOverride 则优先使用，否则使用隐藏的虚拟附魔
        if (icon.isGlowing()) {
            meta = addGlint(meta);
        }

        // 用于精简 GUI 物品的提示信息标志
        try {
            meta.addItemFlags(
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_DESTROYS,
                    ItemFlag.HIDE_PLACED_ON
            );
        } catch (Throwable ignored) {
            // 部分标志在旧版 API 中不存在；可以安全地忽略
        }

        // 在支持的情况下清除属性修饰符
        try {
            if (meta.hasAttributeModifiers()) {
                // 清空所有修饰符，避免引用旧版中不存在的枚举常量
                meta.setAttributeModifiers(null);
            }
        } catch (Throwable ignored) {
            // 旧版 API 不提供属性修饰符；无需处理
        }

        is.setItemMeta(meta);
        return is;
    }
}