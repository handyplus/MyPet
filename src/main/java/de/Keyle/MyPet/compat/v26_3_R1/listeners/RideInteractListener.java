package de.Keyle.MyPet.compat.v26_3_R1.listeners;

import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.MyPetBukkitEntity;
import de.Keyle.MyPet.api.player.Permissions;
import de.Keyle.MyPet.api.skill.skills.Ride;
import de.Keyle.MyPet.api.util.locale.Translation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import de.Keyle.MyPet.compat.v26_3_R1.entity.CraftMyPet;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * 1.21_R5 专用的骑乘兼容处理：
 * - 在 1.21+ 上，Mojang 修改了拴绳逻辑。我们在这里使用 NMS 的 startRiding 处理骑乘。
 * - 完全保留在 v26_3_R1 模块内，以避免 1.8.8 上的旧版 API 问题。
 */
public class RideInteractListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRideWithConfiguredItem(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getRightClicked() instanceof MyPetBukkitEntity)) {
            return;
        }
        // 只处理主手交互
        try {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
                return;
            }
        } catch (NoSuchMethodError ignored) {
            // 旧版本上不适用，但此类只在 1.21_R5 上加载
        }

        final Player player = event.getPlayer();
        final MyPetBukkitEntity petEntity = (MyPetBukkitEntity) event.getRightClicked();

        if (!isOwner(player, petEntity)) {
            return;
        }

        if (Configuration.Skilltree.Skill.Ride.RIDE_ITEM != null && !Configuration.Skilltree.Skill.Ride.RIDE_ITEM.compare(player.getInventory().getItemInMainHand())) {
            return;
        }

        MyPet myPet = petEntity.getMyPet();
        if (myPet == null || !petEntity.canMove() || !myPet.getSkills().isActive(Ride.class)) {
            return;
        }
        if (!Permissions.hasExtended(player, "MyPet.extended.ride")) {
            myPet.getOwner().sendMessage(Translation.getString("Message.No.CanUse", myPet.getOwner()), 2000);
            return;
        }

        // 在此版本模块中直接使用 NMS 进行骑乘
        try {
            ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
            Entity nmsPet = ((CraftMyPet) petEntity).getHandle();
            if (!petEntity.getPassengers().contains(player)) {
                boolean mounted = nmsPlayer.startRiding(nmsPet, true, true);
                if (mounted) {
                    event.setCancelled(true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonitorRideFinisher(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof MyPetBukkitEntity)) {
            return;
        }
        try {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
                return;
            }
        } catch (NoSuchMethodError ignored) {
        }
        final Player player = event.getPlayer();
        final MyPetBukkitEntity petEntity = (MyPetBukkitEntity) event.getRightClicked();
        if (!isOwner(player, petEntity)) {
            return;
        }
        if (Configuration.Skilltree.Skill.Ride.RIDE_ITEM != null && !Configuration.Skilltree.Skill.Ride.RIDE_ITEM.compare(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.isCancelled() && !petEntity.getPassengers().contains(player)) {
            event.setCancelled(false);
        }
    }

    private static boolean isOwner(Player player, MyPetBukkitEntity petEntity) {
        MyPet apiPet = petEntity.getMyPet();
        return apiPet != null && apiPet.getOwner() != null && apiPet.getOwner().getPlayer() != null
                && apiPet.getOwner().getPlayer().getUniqueId().equals(player.getUniqueId());
    }
}