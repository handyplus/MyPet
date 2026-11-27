/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2025 Keyle
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
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.event.MyPetInventoryActionEvent;
import de.Keyle.MyPet.api.player.Permissions;
import de.Keyle.MyPet.api.skill.UpgradeComputer;
import de.Keyle.MyPet.api.util.inventory.CustomInventory;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.Keyle.MyPet.util.nbt.TagBase;
import de.Keyle.MyPet.util.nbt.TagByte;
import de.Keyle.MyPet.util.nbt.TagCompound;
import de.Keyle.MyPet.util.nbt.TagList;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * 背包宠物技能
 * <p>
 * 该技能为每只宠物提供一个可由主人打开的可扩展背包。背包容量由配置的行数
 * 决定（每行代表 9 个槽位）。其他特性包括：通过 NBT 持久化背包内容、
 * 打开背包时响应环境与权限限制，以及可选的玩家死亡时掉落行为。
 * <p>
 * 健壮性：所有打开操作都会校验环境约束（睡眠中、创造模式限制、位置限制），
 * 并遵循插件事件，使其他组件可以否决该操作。当行数升级通过回调发生变化时，
 * 背包容量会自动重新计算。
 */
public class BackpackImpl implements de.Keyle.MyPet.api.skill.skills.Backpack {

    /**
     * 宠物可用的背包行数。每行等于 9 个槽位。
     * 该值由技能系统控制，可能随时间变化。
     */
    @Getter
    protected UpgradeComputer<Number> rows = new UpgradeComputer<>(0);

    /**
     * 决定宠物死亡时是否掉落背包内的物品。
     * 具体的语义由本类之外的监听器实现。
     */
    @Getter
    protected UpgradeComputer<Boolean> dropOnDeath = new UpgradeComputer<>(false);

    /**
     * 底层背包实现，由与版本相关的兼容层提供。
     * 返回底层的 CustomInventory，以便直接交互（例如掉落、
     * 设置/获取物品，或查询 Bukkit 物品栏）。
     */
    @Getter
    protected CustomInventory inventory;

    /** 所属宠物。 */
    @Getter
    protected MyPet myPet;

    /**
     * 为指定宠物创建一个新的背包技能实例，并挂接容量回调，
     * 使背包容量跟随配置的行数。
     *
     * @param myPet 所属宠物
     */
    public BackpackImpl(MyPet myPet) {
        this.myPet = myPet;
        inventory = MyPetApi.getCompatUtil().getCompatInstance(CustomInventory.class, "util.inventory", "CustomInventory");
        // 确保背包立即反映当前的行数
        if(rows.getValue().intValue() > 0) {
            inventory.setSize(rows.getValue().intValue() * 9);
        }
        // 保持容量与将来的行数变化同步（始终注册回调）
        rows.addCallback((newValue, reason) -> this.inventory.setSize(newValue.intValue() * 9));
    }

    /**
     * 描述行数的、对玩家友好的字符串，并按给定的语言代码本地化。
     *
     * @param locale 用于翻译的语言/地区代码
     * @return 美观的、本地化的行数表示
     */
    public String toPrettyString(String locale) {
        return "" + ChatColor.GOLD + rows.getValue() + ChatColor.RESET + " " + Translation.getString("Name.Rows", locale);
    }

    /**
     * 背包升级后展示给玩家的消息。
     * 包含新的槽位数量（行数 * 9）。
     *
     * @return 组成升级消息的若干行文本
     */
    @Override
    public String[] getUpgradeMessage() {
        return new String[]{
                Util.formatText(Translation.getString("Message.Skill.Inventory.Upgrade", myPet.getOwner()), myPet.getPetName(), getRows().getValue().intValue() * 9)
        };
    }

    /**
     * 尝试为拥有者打开背包。该操作会执行多项检查：
     * - 背包必须至少有一行。
     * - 拥有者不能处于睡眠状态。
     * - 创造模式可能受配置/权限限制。
     * - 当前位置必须允许打开（不能在液体方块内）。
     * - 其他插件可通过 MyPetInventoryActionEvent 取消该操作。
     *
     * @return 若成功打开背包则返回 true；否则返回 false
     */
    public boolean activate() {
        if (rows.getValue().intValue() > 0) {
            if (myPet.getOwner().getPlayer().isSleeping()) {
                myPet.getOwner().sendMessage(Translation.getString("Message.No.CanUse", myPet.getOwner()));
                return false;
            }
            if (myPet.getOwner().getPlayer().getGameMode() == GameMode.CREATIVE && !Configuration.Skilltree.Skill.Backpack.OPEN_IN_CREATIVE && !Permissions.has(myPet.getOwner().getPlayer(), "MyPet.admin", false)) {
                myPet.getOwner().sendMessage(Translation.getString("Message.Skill.Inventory.Creative", myPet.getOwner()));
                return false;
            }
            MyPetInventoryActionEvent event = new MyPetInventoryActionEvent(myPet, MyPetInventoryActionEvent.Action.Open);
            Bukkit.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.No.AllowedHere", myPet.getOwner()), myPet.getPetName()));
                return false;
            }
            if (myPet.getLocation().isPresent() && !myPet.getLocation().get().getBlock().isLiquid()) {
                openInventory(myPet.getOwner().getPlayer());
                return true;
            } else {
                myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skill.Inventory.Swimming", myPet.getOwner()), myPet.getPetName()));
                return false;
            }
        } else {
            myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skill.Inventory.NotAvailable", myPet.getOwner()), myPet.getPetName()));
            return false;
        }
    }

    /**
     * 先把物品栏标题设为宠物的当前名称，再为给定玩家打开底层物品栏 GUI。
     *
     * @param p 要为其打开物品栏的玩家
     */
    public void openInventory(Player p) {
        inventory.setName(myPet.getPetName());
        inventory.open(p);
    }

    /**
     * 若背包物品栏当前处于打开状态则将其关闭。
     */
    public void closeInventory() {
        inventory.close();
    }

    /**
     * 从给定的 NBT 标签恢复物品栏状态。
     *
     * @param tag 要加载的标签
     */
    public void load(TagCompound tag) {
        // 在加载物品前确保底层物品栏存在且容量正确。处于满仓状态时被缩小过的背包
        // 会以多于其行数的槽位数保存，因此要为每个已保存的槽位留出空间，
        // 而不是丢弃最后一行之后的那些槽位中的物品。
        int size = rows.getValue().intValue() * 9;
        int highestSlot = highestSavedSlot(tag);
        if (highestSlot >= size) {
            size = ((highestSlot + 1 + 8) / 9) * 9;
        }
        inventory.setSize(size);
        inventory.load(tag);
    }

    private static int highestSavedSlot(TagCompound tag) {
        int highest = -1;
        TagList items = tag.getAs("Items", TagList.class);
        if (items == null) {
            return highest;
        }
        for (int i = 0; i < items.size(); i++) {
            TagBase item = items.getReadOnlyList().get(i);
            if (item instanceof TagCompound) {
                TagBase slot = ((TagCompound) item).getCompoundData().get("Slot");
                if (slot instanceof TagByte) {
                    highest = Math.max(highest, ((TagByte) slot).getByteData());
                }
            }
        }
        return highest;
    }

    /**
     * 将当前物品栏状态序列化进一个新的 NBT 标签。
     *
     * @return 包含物品栏内容与元数据的 NBT 标签
     */
    public TagCompound save() {
        TagCompound nbtTagCompound = new TagCompound();
        inventory.save(nbtTagCompound);
        return nbtTagCompound;
    }

    /**
     * 指示该技能当前是否可用（即至少有一行可用）。
     *
     * @return 若背包有容量则返回 true；否则返回 false
     */
    public boolean isActive() {
        return rows.getValue().intValue() > 0;
    }

    /**
     * 重置应用到该技能上的所有升级（行数与死亡掉落）。
     * 不会清空物品栏内容。
     */
    @Override
    public void reset() {
        rows.removeAllUpgrades();
        dropOnDeath.removeAllUpgrades();
    }

    /**
     * 便于调试的当前技能状态表示。
     */
    @Override
    public String toString() {
        return "BackpackImpl{" +
                "rows=" + rows +
                ", dropOnDeath=" + dropOnDeath +
                '}';
    }
}