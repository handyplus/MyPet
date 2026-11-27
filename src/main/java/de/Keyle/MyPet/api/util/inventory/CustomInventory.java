/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2025 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License,
 * as published by the Free Software Foundation, either version 3 of the License, or
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

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.util.nbt.TagByte;
import de.Keyle.MyPet.util.nbt.TagCompound;
import de.Keyle.MyPet.util.nbt.TagList;
import de.Keyle.MyPet.util.nbt.TagString;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.util.*;

/**
 * 仅使用 API 的物品栏实现
 * <p>
 * 该类提供设置大小、命名、持久化（到 K-NBT）以及为玩家打开/关闭 GUI 的
 * 方法。它避免直接使用 CraftBukkit/NMS，应当能在各种现代
 * Bukkit/Paper 版本上工作。
 * </p>
 */
public class CustomInventory implements InventoryHolder {
    /**
     * 存储实际内容的 Bukkit 物品栏。
     */
    private Inventory bukkitInventory;

    /**
     * 物品栏 GUI 当前的显示名称（标题）。
     * 默认为 "Inventory"。
     */
    @Getter
    private String name = "Inventory";
    /**
     * 物品栏的逻辑大小（以槽位计）。始终是 9 的倍数，且位于闭区间
     * [9, 54] 内。
     * 默认为 null。
     */
    @Getter
    private int size;
    /**
     * 应用于底层 Bukkit 物品栏的最大堆叠数量。
     * 默认为 64。
     */
    private int stackSize = 64;
    /**
     * 辅助的查看者列表，用于在底层物品栏可能尚未初始化时
     * 跟踪打开/关闭状态。
     */
    private final List<HumanEntity> transaction = new ArrayList<>();


    /**
     * 跟踪实例并注册一个共享的关闭监听器（不为每个实例单独注册监听器）
     */
    private static final Set<CustomInventory> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean LISTENER_REGISTERED = false;
    private static final Listener SHUTDOWN_LISTENER = new Listener() {
        @EventHandler
        public void onPluginDisable(PluginDisableEvent e) {
            if (!e.getPlugin().equals(MyPetApi.getPlugin())) return;
            closeAllOpen();
            INSTANCES.clear();
            LISTENER_REGISTERED = false;
        }
    };

    /**
     * 确保该插件只注册一次共享的关闭监听器。
     * 线程安全，防止并发初始化。
     */
    private static synchronized void ensureListener() {
        if (!LISTENER_REGISTERED) {
            Bukkit.getPluginManager().registerEvents(SHUTDOWN_LISTENER, MyPetApi.getPlugin());
            LISTENER_REGISTERED = true;
        }
    }

    /**
     * 关闭当前跟踪的所有 CustomInventory 实例中已打开的物品栏。
     * 由共享关闭监听器在插件禁用时调用。
     */
    static void closeAllOpen() {
        for (CustomInventory ci : new ArrayList<>(INSTANCES)) {
            if (ci != null) ci.close();
        }
    }

    /**
     * 创建新的 CustomInventory 实例并确保共享关闭监听器
     * 已完成注册。该实例也会被跟踪以便管理生命周期，从而在
     * 插件禁用时能够关闭所有已打开的物品栏。
     */
    public CustomInventory() {
        ensureListener();
        INSTANCES.add(this);
    }

    /**
     * 便捷构造器，用于初始化大小和名称。
     *
     * @param size 初始大小（以槽位计，会被归一化为 9 的倍数并限制在 [9,54] 内）
     * @param name 初始 GUI 标题（超过 64 个字符时会被截断）
     */
    public CustomInventory(int size, String name) {
        this();
        setSize(size);
        setName(name);
    }

    /**
     * 设置物品栏大小（以槽位计）。
     * <p>
     * 该值会被限制为 [9, 54] 之间的 9 的倍数
     *
     * @param newSize 期望的槽位数量
     */
    public void setSize(int newSize) {
        newSize = (int) (newSize / 9.);
        newSize *= 9;
        newSize = Util.clamp(newSize, 9, 54);

        // 在复制旧物品栏之前关闭它的每个视图。若查看者停留在
        // 旧物品栏上，他会继续取出原始物品，而新物品栏中保存的是
        // 这些物品的副本，从而把整个背包复制一遍。
        List<HumanEntity> viewers = bukkitInventory != null
                ? new ArrayList<>(bukkitInventory.getViewers())
                : Collections.<HumanEntity>emptyList();
        for (HumanEntity viewer : viewers) {
            viewer.closeInventory();
        }

        ItemStack[] old = bukkitInventory != null ? bukkitInventory.getContents() : null;

        // 缩小后的容量不得小于保存所有已存堆叠所需的数量。
        if (old != null) {
            int used = 0;
            for (ItemStack item : old) {
                if (item != null && item.getType() != Material.AIR) {
                    used++;
                }
            }
            if (used > newSize) {
                newSize = Util.clamp(((used + 8) / 9) * 9, 9, 54);
            }
        }
        this.size = newSize;

        this.bukkitInventory = Bukkit.createInventory(this, this.size, this.name);
        this.bukkitInventory.setMaxStackSize(this.stackSize);
        if (old != null) {
            List<ItemStack> moved = new ArrayList<>();
            for (int i = 0; i < old.length; i++) {
                ItemStack item = old[i];
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                if (i < this.size) {
                    this.bukkitInventory.setItem(i, item);
                } else {
                    moved.add(item);
                }
            }
            // 被移除行中的堆叠会放入最前面的空槽位。
            for (ItemStack item : moved) {
                int free = this.bukkitInventory.firstEmpty();
                if (free < 0) {
                    break;
                }
                this.bukkitInventory.setItem(free, item);
            }
        }

        for (HumanEntity viewer : viewers) {
            if (viewer instanceof Player && ((Player) viewer).isOnline()) {
                viewer.openInventory(this.bukkitInventory);
            }
        }
    }

    /**
     * 设置物品栏 GUI 标题。
     * <p>
     * 重命名会重建底层的 Bukkit 物品栏以应用新标题，
     * 同时保留内容与最大堆叠数量。
     *
     * @param name 新标题（null 会被忽略）
     */
    public void setName(String name) {
        if (name == null) return;

        // 强制合理的标题长度并移除控制字符
        String cleaned = name.replaceAll("\\p{Cntrl}", "");
        String newName = cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;

        if (Objects.equals(this.name, newName)) return;

        this.name = newName;

        // 如果还没有底层物品栏，则只缓存标题
        if (bukkitInventory == null) {
            return;
        }

        // 保留内容与查看者，然后用新标题重建物品栏。
        ItemStack[] contents = bukkitInventory.getContents();
        List<HumanEntity> viewers = new ArrayList<>(bukkitInventory.getViewers());

        this.bukkitInventory = Bukkit.createInventory(this, this.size, this.name);
        this.bukkitInventory.setMaxStackSize(this.stackSize);
        this.bukkitInventory.setContents(contents);

        // 为所有查看者重新打开，使新标题立即生效。
        for (HumanEntity viewer : viewers) {
            if (viewer instanceof Player) {
                viewer.openInventory(this.bukkitInventory);
            }
        }
    }

    /**
     * 如果尚未创建，则为该 CustomInventory 实例创建一个 Bukkit 物品栏。
     */
    private void createInventoryIfNeeded() {
        if (this.size <= 0) {
            return;
        }
        if (this.bukkitInventory == null || this.bukkitInventory.getSize() != this.size || this.bukkitInventory.getHolder() != this) {
            ItemStack[] contents = this.bukkitInventory != null ? this.bukkitInventory.getContents() : null;
            this.bukkitInventory = Bukkit.createInventory(this, this.size, this.name);
            this.bukkitInventory.setMaxStackSize(this.stackSize);
            if (contents != null) {
                this.bukkitInventory.setContents(contents);
            }
        }
    }

    /**
     * 获取给定槽位索引当前存储的物品。
     *
     * @param i 从 0 开始的槽位索引
     * @return 该槽位中的物品；若越界、未初始化或为空则返回 null
     */
    public ItemStack getItem(int i) {
        if (this.bukkitInventory == null) return null;
        if (i < 0 || i >= this.size) return null;
        return this.bukkitInventory.getItem(i);
    }

    /**
     * 设置指定槽位的物品。
     *
     * @param i         从 0 开始的槽位索引
     * @param itemStack  要设置的物品（可以为 null 以清空）
     */
    public void setItem(int i, ItemStack itemStack) {
        createInventoryIfNeeded();
        if (this.bukkitInventory == null) return;
        if (i < 0 || i >= this.size) return;
        this.bukkitInventory.setItem(i, itemStack);
    }

    /**
     * 尝试把给定的物品堆叠加入物品栏。
     * <p>
     * 委托给 Bukkit 的 Inventory#addItem，并返回因空间不足
     * 而未能放入的物品数量。
     *
     * @param itemAdd 要添加的堆叠（为 null 或空气时剩余量为 0）
     * @return 未能添加的数量（全部放入时为 0）
     */
    public int addItem(ItemStack itemAdd) {
        if (itemAdd == null || itemAdd.getType() == Material.AIR) {
            return 0;
        }
        createInventoryIfNeeded();
        if (this.bukkitInventory == null) {
            return itemAdd.getAmount();
        }
        ItemStack toAdd = itemAdd.clone();
        Map<Integer, ItemStack> leftover = this.bukkitInventory.addItem(toAdd);
        int remaining = 0;
        for (ItemStack rem : leftover.values()) {
            if (rem != null) remaining += rem.getAmount();
        }
        return remaining;
    }

    /**
     * 返回底层的 Bukkit Inventory 实例，供需要直接访问的
     * 集成点使用。
     *
     * @return 底层 Bukkit 物品栏，必要时会创建它
     */
    public Inventory getBukkitInventory() {
        return getInventory();
    }

    /**
     * InventoryHolder 契约。返回其持有的 Bukkit Inventory，若已设置大小
     * 则延迟创建。持有者始终是该实例本身。
     *
     * @return 持有的 Bukkit 物品栏；若尚未设置大小则返回 null
     */
    @Override
    public Inventory getInventory() {
        return bukkitInventory;
    }

    /**
     * 将该物品栏中所有非空气内容掉落到世界中给定位置，
     * 并清空这些槽位。
     *
     * @param loc 物品应生成的世界位置（为 null 或缺少世界时忽略）
     */
    public void dropContentAt(Location loc) {
        createInventoryIfNeeded();
        if (loc == null) return;
        World world = loc.getWorld();
        if (world == null) return;
        for (int i = 0; i < this.getSize(); i++) {
            ItemStack is = this.removeItemNoUpdate(i);
            if (is != null && is.getType() != Material.AIR) {
                world.dropItem(loc, is);
            }
        }
    }

    /**
     * 从指定槽位中移除最多请求数量的物品，并把移除的物品
     * 作为独立的堆叠返回。
     *
     * @param slot     从 0 开始的槽位索引
     * @param subtract 从该槽位取出的最大物品数量
     * @return 表示被移除物品的堆叠；若槽位为空或越界则返回 null
     */
    public ItemStack removeItem(int slot, int subtract) {
        createInventoryIfNeeded();
        if (this.bukkitInventory == null) return null;
        if (slot < 0 || slot >= this.size) return null;
        ItemStack current = this.bukkitInventory.getItem(slot);
        if (current == null || current.getType() == Material.AIR) return null;
        int take = Math.min(subtract, current.getAmount());
        ItemStack result = current.clone();
        result.setAmount(take);
        int remaining = current.getAmount() - take;
        if (remaining <= 0) {
            this.bukkitInventory.clear(slot);
        } else {
            current.setAmount(remaining);
            this.bukkitInventory.setItem(slot, current);
        }
        return result;
    }

    /**
     * 返回物品栏当前内容的副本。
     *
     * @return 包含当前槽位内容、可修改的列表（可能包含 null）
     */
    public List<ItemStack> getContents() {
        if (this.bukkitInventory == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(this.bukkitInventory.getContents()));
    }

    /**
     * 将物品栏内容序列化到给定的 K-NBT 复合标签中。
     * <p>
     * 每个非空槽位都以 Base64 编码的 Bukkit ItemStack 形式存储在键
     * "BukkitItem" 下，同时记录其槽位索引。
     *
     * @param compound 要写入的目标复合标签（不能为 null）
     * @return 同一个复合标签实例，便于链式调用
     */
    public TagCompound save(TagCompound compound) {
        createInventoryIfNeeded();
        List<TagCompound> itemList = new ArrayList<>();
        if (this.bukkitInventory == null) {
            compound.getCompoundData().put("Items", new TagList(itemList));
            return compound;
        }
        for (int i = 0; i < this.bukkitInventory.getSize(); i++) {
            ItemStack itemStack = this.bukkitInventory.getItem(i);
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                TagCompound item = new TagCompound();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
                    oos.writeObject(itemStack);
                    oos.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
                item.getCompoundData().put("Slot", new TagByte((byte) i));
                item.getCompoundData().put("BukkitItem", new TagString(b64));
                itemList.add(item);
            }
        }
        compound.getCompoundData().put("Items", new TagList(itemList));
        return compound;
    }

    /**
     * 从 {@link #save(TagCompound)} 创建的 K-NBT 复合标签中恢复物品栏内容。
     *
     * @param nbtTagCompound 包含 "Items" 列表的源复合标签
     */
    public void load(TagCompound nbtTagCompound) {
        createInventoryIfNeeded();
        TagList items = nbtTagCompound.getAs("Items", TagList.class);
        if (items == null) return;
        if (this.bukkitInventory == null) return;
        for (int i = 0; i < items.size(); i++) {
            TagCompound itemCompound = items.getTagAs(i, TagCompound.class);
            TagByte slotTag = itemCompound.getAs("Slot", TagByte.class);
            TagString b64Tag = itemCompound.getAs("BukkitItem", TagString.class);
            if (slotTag == null || b64Tag == null) {
                continue; // 跳过缺少必需标签的物品
            }
            int slot = slotTag.getByteData();
            String b64 = b64Tag.getStringData();
            byte[] bytes = Base64.getDecoder().decode(b64);
            ItemStack itemStack = null;
            try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object obj = ois.readObject();
                if (obj instanceof ItemStack) {
                    itemStack = (ItemStack) obj;
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (slot >= 0 && slot < this.size) {
                this.bukkitInventory.setItem(slot, itemStack);
            }
        }
    }

    /**
     * 当玩家打开该物品栏时应调用此方法，以在底层物品栏可能被延迟创建时
     * 保持查看者列表同步。
     *
     * @param who 打开该物品栏的人类实体
     */
    public void onOpen(HumanEntity who) {
        this.transaction.add(who);
    }

    /**
     * 当玩家关闭该物品栏时应调用此方法，将其从跟踪的查看者列表中
     * 移除。
     *
     * @param who 关闭该物品栏的人类实体
     */
    public void onClose(HumanEntity who) {
        this.transaction.remove(who);
    }


    /**
     * 为当前所有查看者关闭该物品栏（若有）。
     */
    public void close() {
        for (HumanEntity viewer : new ArrayList<>(getViewers())) {
            viewer.closeInventory();
        }
    }

    /**
     * 为指定玩家打开该物品栏 GUI。
     *
     * @param player 应当看到该物品栏的玩家
     */
    public void open(Player player) {
        createInventoryIfNeeded();
        Inventory inv = getBukkitInventory();
        if (inv != null) {
            player.openInventory(inv);
        }
    }

    /**
     * 返回该物品栏当前的查看者。如果底层物品栏尚未创建，
     * 则回退到内部跟踪的打开者列表。
     *
     * @return 当前正在查看该物品栏的人类实体列表
     */

    public List<HumanEntity> getViewers() {
        if (bukkitInventory != null) return bukkitInventory.getViewers();
        return new ArrayList<>(transaction); // 软回退，不额外分配
    }

    /**
     * 返回底层物品栏的 InventoryHolder。始终为 {@code this}。
     *
     * @return 该实例本身
     */
    public InventoryHolder getOwner() {
        return this;
    }

    /**
     * 获取底层 Bukkit 物品栏使用的最大堆叠数量。
     *
     * @return 最大堆叠数量（初始化前默认为 64）
     */
    public int getMaxStackSize() {
        return this.bukkitInventory != null ? this.bukkitInventory.getMaxStackSize() : this.stackSize;
    }

    /**
     * 设置应用于底层 Bukkit 物品栏的最大堆叠数量，并更新
     * 初始化前使用的缓存值。
     *
     * @param i 新的最大堆叠数量
     */
    public void setMaxStackSize(int i) {
        this.stackSize = i;
        createInventoryIfNeeded();
        if (this.bukkitInventory != null) {
            this.bukkitInventory.setMaxStackSize(i);
        }
    }

    /**
     * 移除并返回指定槽位中当前的物品，不触发任何
     * 额外逻辑。
     *
     * @param i 从 0 开始的槽位索引
     * @return 该槽位之前的物品；若为空或越界则返回 null
     */
    public ItemStack removeItemNoUpdate(int i) {
        createInventoryIfNeeded();
        if (this.bukkitInventory == null) return null;
        ItemStack current = this.bukkitInventory.getItem(i);
        if (current == null) return null;
        this.bukkitInventory.clear(i);
        return current;
    }

    /**
     * 检查物品栏是否不包含任何物品（所有槽位为 null 或空气）。
     *
     * @return 如果物品栏中没有有效物品则返回 true，否则返回 false
     */
    public boolean isEmpty() {
        if (this.bukkitInventory == null) {
            return true;
        }
        for (ItemStack is : this.bukkitInventory.getContents()) {
            if (is != null && is.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }
}