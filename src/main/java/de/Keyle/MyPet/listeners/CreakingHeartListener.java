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

package de.Keyle.MyPet.listeners;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.WorldGroup;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.MyPetType;
import de.Keyle.MyPet.api.entity.leashing.LeashFlag;
import de.Keyle.MyPet.api.event.MyPetCreateEvent;
import de.Keyle.MyPet.api.event.MyPetSaveEvent;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.player.Permissions;
import de.Keyle.MyPet.api.repository.RepositoryCallback;
import de.Keyle.MyPet.api.util.ConfigItem;
import de.Keyle.MyPet.api.util.configuration.settings.Settings;
import de.Keyle.MyPet.api.util.hooks.types.LeashEntityHook;
import de.Keyle.MyPet.api.util.hooks.types.LeashHook;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.Keyle.MyPet.api.util.service.types.EntityConverterService;
import de.Keyle.MyPet.entity.InactiveMyPet;
import de.Keyle.MyPet.util.translation.PetDefaultNameResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * 监听器：当与嘎枝之心相连的嘎枝实体所对应的嘎枝之心方块被摧毁时，捕捉该实体。
 * <p>
 * 在原版 Minecraft 中，与之心相连的嘎枝免疫伤害，只能通过摧毁与之相连的嘎枝之心方块来击杀。
 * 本监听器拦截之心方块的破坏事件，并把与之相连的实体转换为宠物，从而让 MyPet 能够捕捉这些嘎枝。
 */
public class CreakingHeartListener implements Listener {

    // 32 格与原版嘎枝之心的连接范围一致
    private static final int SEARCH_RADIUS = 32;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreakingHeartBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // 检查被破坏的方块是否为嘎枝之心
        if (block.getType() != Material.CREAKING_HEART) {
            return;
        }

        Player player = event.getPlayer();

        if (WorldGroup.getGroupByWorld(player.getWorld()).isDisabled()) {
            return;
        }

        // 查找与之相连的嘎枝实体
        LivingEntity linkedCreaking = findLinkedCreaking(block);
        if (linkedCreaking == null) {
            return;
        }

        // 玩家已有一只激活的宠物
        if (MyPetApi.getMyPetManager().hasActiveMyPet(player)) {
            return;
        }

        MyPetType petType = MyPetType.byEntityTypeName(linkedCreaking.getType().name());

        // 仅当 HeartLinked 被配置为捕捉条件时，才允许基于之心的捕捉
        if (!isHeartLinkedRequired(petType)) {
            return;
        }

        ConfigItem neededLeashItem = MyPetApi.getMyPetInfo().getLeashItem(petType);

        // 检查权限
        if (!Permissions.has(player, "MyPet.leash." + petType.name())) {
            return;
        }

        // 检查主手中的捕捉物品
        ItemStack leashItem = player.getInventory().getItemInMainHand();
        if (neededLeashItem != null && !neededLeashItem.compare(leashItem)) {
            return;
        }

        // 执行 LeashHook 检查
        for (LeashHook hook : MyPetApi.getPluginHookManager().getHooks(LeashHook.class)) {
            if (!hook.canLeash(player, linkedCreaking)) {
                return;
            }
        }

        // 以 damage=0 执行 LeashFlag 检查（基于之心的捕捉）
        boolean willBeLeashed = true;
        MyPetPlayer myPetPlayer = null;
        if (MyPetApi.getPlayerManager().isMyPetPlayer(player)) {
            myPetPlayer = MyPetApi.getPlayerManager().getMyPetPlayer(player);
        }

        for (Settings flagSettings : MyPetApi.getMyPetInfo().getLeashFlagSettings(petType)) {
            String flagName = flagSettings.getName();
            LeashFlag flag = MyPetApi.getLeashFlagManager().getLeashFlag(flagName);
            if (flag == null) {
                MyPetApi.getLogger().warning("\"" + flagName + "\" is not a valid leash requirement!");
                continue;
            }
            // 用 damage=0 表示基于之心的捕捉
            if (!flag.check(player, linkedCreaking, 0, flagSettings)) {
                willBeLeashed = false;
                if (myPetPlayer != null && myPetPlayer.isCaptureHelperActive()) {
                    String message = flag.getMissingMessage(player, linkedCreaking, 0, flagSettings);
                    if (message != null) {
                        myPetPlayer.sendMessage(LeashFlag.getMessagePrefix(false) + message, 10000);
                    }
                }
            } else {
                if (myPetPlayer != null && myPetPlayer.isCaptureHelperActive()) {
                    String message = flag.getMissingMessage(player, linkedCreaking, 0, flagSettings);
                    if (message != null) {
                        myPetPlayer.sendMessage(LeashFlag.getMessagePrefix(true) + message, 10000);
                    }
                }
            }
        }

        if (!willBeLeashed) {
            return;
        }

        // 取消事件并手动处理方块破坏，以避免竞态条件
        // 这样能保证实体在之心被破坏之前先被移除（避免原版死亡特效）
        event.setCancelled(true);

        // 创建宠物
        final MyPetPlayer owner;
        if (MyPetApi.getPlayerManager().isMyPetPlayer(player)) {
            owner = MyPetApi.getPlayerManager().getMyPetPlayer(player);
        } else {
            owner = MyPetApi.getPlayerManager().registerMyPetPlayer(player);
        }

        final InactiveMyPet inactiveMyPet = new InactiveMyPet(owner);
        inactiveMyPet.setPetType(petType);
        inactiveMyPet.setPetName(PetDefaultNameResolver.resolve(petType, inactiveMyPet.getOwner()));

        WorldGroup worldGroup = WorldGroup.getGroupByWorld(player.getWorld().getName());
        inactiveMyPet.setWorldGroup(worldGroup.getName());
        inactiveMyPet.getOwner().setMyPetForWorldGroup(worldGroup, inactiveMyPet.getUUID());

        Optional<EntityConverterService> converter = MyPetApi.getServiceManager().getService(EntityConverterService.class);
        converter.ifPresent(service -> inactiveMyPet.setInfo(service.convertEntity(linkedCreaking)));

        // 移除前先记录位置
        final Location capturedEntityLocation = linkedCreaking.getLocation().clone();

        // 在破坏之心之前执行 LeashEntityHook.prepare() 并移除实体，
        // 以避免原版死亡特效
        for (LeashEntityHook hook : MyPetApi.getPluginHookManager().getHooks(LeashEntityHook.class)) {
            hook.prepare(linkedCreaking);
        }
        linkedCreaking.remove();

        // 现在破坏之心方块（前面已取消事件）
        // 创造模式下直接移除方块且不掉落物品（原版行为）
        if (player.getGameMode() == GameMode.CREATIVE) {
            block.setType(Material.AIR);
        } else {
            block.breakNaturally(leashItem);
        }

        // 消耗捕捉物品
        if (Configuration.Misc.CONSUME_LEASH_ITEM && player.getGameMode() != GameMode.CREATIVE && leashItem != null) {
            if (leashItem.getAmount() > 1) {
                leashItem.setAmount(leashItem.getAmount() - 1);
            } else {
                player.getEquipment().setItemInMainHand(null);
            }
        }

        // 触发事件
        MyPetCreateEvent createEvent = new MyPetCreateEvent(inactiveMyPet, MyPetCreateEvent.Source.Leash);
        Bukkit.getServer().getPluginManager().callEvent(createEvent);

        MyPetSaveEvent saveEvent = new MyPetSaveEvent(inactiveMyPet);
        Bukkit.getServer().getPluginManager().callEvent(saveEvent);

        // 保存并激活
        MyPetApi.getPlugin().getRepository().addMyPet(inactiveMyPet, new RepositoryCallback<Boolean>() {
            @Override
            public void callback(Boolean value) {
                if (value == null || !value) {
                    MyPetApi.getLogger().warning("Failed to save captured Creaking pet for " + owner.getName());
                    return;
                }

                owner.sendMessage(Translation.getString("Message.Leash.Add", owner));

                Optional<MyPet> myPet = MyPetApi.getMyPetManager().activateMyPet(inactiveMyPet);
                myPet.ifPresent(pet -> pet.createEntity(capturedEntityLocation));
                if (owner.isCaptureHelperActive()) {
                    owner.setCaptureHelperActive(false);
                    owner.sendMessage(Util.formatText(Translation.getString("Message.Command.CaptureHelper.Mode", owner), Translation.getString("Name.Disabled", owner)));
                }
            }
        });
    }

    /**
     * 当开启捕捉助手的玩家与嘎枝之心交互时，显示捕捉条件。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreakingHeartInteract(PlayerInteractEvent event) {
        // 只处理对方块的右键点击
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CREAKING_HEART) {
            return;
        }

        Player player = event.getPlayer();

        // 检查捕捉助手是否开启
        if (!MyPetApi.getPlayerManager().isMyPetPlayer(player)) {
            return;
        }
        MyPetPlayer myPetPlayer = MyPetApi.getPlayerManager().getMyPetPlayer(player);
        if (!myPetPlayer.isCaptureHelperActive()) {
            return;
        }

        if (WorldGroup.getGroupByWorld(player.getWorld()).isDisabled()) {
            return;
        }

        // 查找与之相连的嘎枝实体
        LivingEntity linkedCreaking = findLinkedCreaking(block);
        if (linkedCreaking == null) {
            myPetPlayer.sendMessage(LeashFlag.getMessagePrefix(false) + "No Creaking linked to this heart", 2000);
            return;
        }

        MyPetType petType = MyPetType.Creaking;

        // 仅当 HeartLinked 被配置为捕捉条件时，才显示基于之心的捕捉信息
        if (!isHeartLinkedRequired(petType)) {
            return;
        }

        // 检查权限
        if (!Permissions.has(player, "MyPet.leash." + petType.name())) {
            myPetPlayer.sendMessage(LeashFlag.getMessagePrefix(false) + Translation.getString("Message.No.Allowed", player), 2000);
            return;
        }

        // 检查捕捉物品
        ConfigItem neededLeashItem = MyPetApi.getMyPetInfo().getLeashItem(petType);
        if (neededLeashItem == null) {
            return;
        }
        ItemStack leashItem = player.getInventory().getItemInMainHand();
        String itemName = neededLeashItem.getItem().getType().name().toLowerCase().replace("_", " ");
        if (!neededLeashItem.compare(leashItem)) {
            myPetPlayer.sendMessage(LeashFlag.getMessagePrefix(false) + "Hold a " + itemName, 2000);
        } else {
            myPetPlayer.sendMessage(LeashFlag.getMessagePrefix(true) + "Holding " + itemName, 2000);
        }

        // 执行 LeashHook 检查
        for (LeashHook hook : MyPetApi.getPluginHookManager().getHooks(LeashHook.class)) {
            if (!hook.canLeash(player, linkedCreaking)) {
                return;
            }
        }

        // 显示 LeashFlag 状态
        for (Settings flagSettings : MyPetApi.getMyPetInfo().getLeashFlagSettings(petType)) {
            String flagName = flagSettings.getName();
            LeashFlag flag = MyPetApi.getLeashFlagManager().getLeashFlag(flagName);
            if (flag == null) {
                continue;
            }
            // 用 damage=0 表示基于之心的捕捉检查
            boolean passed = flag.check(player, linkedCreaking, 0, flagSettings);
            String message = flag.getMissingMessage(player, linkedCreaking, 0, flagSettings);
            if (message != null) {
                myPetPlayer.sendMessage(LeashFlag.getMessagePrefix(passed) + message, 2000);
            }
        }

        // 玩家已有一只激活的宠物
        if (MyPetApi.getMyPetManager().hasActiveMyPet(player)) {
            myPetPlayer.sendMessage(LeashFlag.getMessagePrefix(false) + Translation.getString("Message.Command.CaptureHelper.HasPet", player), 2000);
        }
    }

    /**
     * 检查 HeartLinked 捕捉条件是否已配置为指定宠物种类的条件。
     */
    private boolean isHeartLinkedRequired(MyPetType petType) {
        for (Settings flagSettings : MyPetApi.getMyPetInfo().getLeashFlagSettings(petType)) {
            if ("HeartLinked".equals(flagSettings.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取嘎枝实体的巢穴位置。
     *
     * @param entity 要获取巢穴位置的实体
     * @return 巢穴位置；若非嘎枝或未设置巢穴则返回 null
     */
    private static Location getCreakingHome(Entity entity) {
        if (entity instanceof Creaking) {
            try { return ((Creaking) entity).getHome(); } catch (LinkageError ignored) { return null; } // 部分 1.21.4 版本的构建中可能没有 getHome()
        }
        return null;
    }

    /**
     * 查找巢穴位置与给定方块位置匹配的嘎枝实体。
     *
     * @param heartBlock 被破坏的嘎枝之心方块
     * @return 相连的嘎枝实体；未找到则返回 null
     */
    private LivingEntity findLinkedCreaking(Block heartBlock) {
        Location heartLocation = heartBlock.getLocation();

        for (Entity entity : heartBlock.getWorld().getNearbyEntities(heartLocation, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            // 检查实体类型
            if (entity.getType() != EntityType.CREAKING) {
                continue;
            }

            if (!(entity instanceof LivingEntity)) {
                continue;
            }

            Location homePos = getCreakingHome(entity);
            if (homePos != null &&
                homePos.getWorld() != null &&
                homePos.getWorld().equals(heartLocation.getWorld()) &&
                homePos.getBlockX() == heartLocation.getBlockX() &&
                homePos.getBlockY() == heartLocation.getBlockY() &&
                homePos.getBlockZ() == heartLocation.getBlockZ()) {
                return (LivingEntity) entity;
            }
        }
        return null;
    }
}
