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

package de.Keyle.MyPet.api;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.compat.Compat;
import de.Keyle.MyPet.api.entity.MyPetMinecraftEntity;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.util.inventory.material.MaterialHolder;
import de.Keyle.MyPet.util.nbt.TagCompound;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.util.locale.Translation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.Files;
import java.util.UUID;

public abstract class PlatformHelper {

    /**
     * @param location 玩家必须在 {@link Location} 附近才能看到该效果
     * @param effect   效果列表：https://gist.github.com/riking/5759002
     * @param offsetX  在 X 轴上的随机偏移量
     * @param offsetY  在 Y 轴上的随机偏移量
     * @param offsetZ  在 Z 轴上的随机偏移量
     * @param speed    粒子的速度
     * @param count    粒子数量
     * @param radius   该位置周围的半径
     */
    public abstract void playParticleEffect(Location location, String effect, float offsetX, float offsetY, float offsetZ, float speed, int count, int radius, Compat<Object> data);

    public void playParticleEffect(Location location, String effect, float offsetX, float offsetY, float offsetZ, float speed, int count, int radius) {
        playParticleEffect(location, effect, offsetX, offsetY, offsetZ, speed, count, radius, null);
    }

    /**
     * @param location 玩家必须在 {@link Location} 附近才能看到该效果
     * @param effect   效果列表：https://gist.github.com/riking/5759002
     * @param offsetX  在 X 轴上的随机偏移量
     * @param offsetY  在 Y 轴上的随机偏移量
     * @param offsetZ  在 Z 轴上的随机偏移量
     * @param speed    粒子的速度
     * @param count    粒子数量
     * @param radius   该位置周围的半径
     */
    public abstract void playParticleEffect(Player player, Location location, String effect, float offsetX, float offsetY, float offsetZ, float speed, int count, int radius, Compat<Object> data);

    public void playParticleEffect(Player player, Location location, String effect, float offsetX, float offsetY, float offsetZ, float speed, int count, int radius) {
        playParticleEffect(player, location, effect, offsetX, offsetY, offsetZ, speed, count, radius, null);
    }

    public abstract boolean canSpawn(Location loc, MyPetMinecraftEntity entity);

    public String getPlayerLanguage(Player player) {
        String locale = player.getLocale();
        if (locale.isEmpty()) {
            return "zh_cn";
        }
        return locale;
    }

    public abstract TagCompound entityToTag(Entity entity);

    public abstract void applyTagToEntity(TagCompound tag, Entity entity);

    public String getCommandSenderLanguage(CommandSender sender) {
        String lang = "zh";
        if (sender instanceof Player) {
            lang = getPlayerLanguage((Player) sender);
        }
        return lang;
    }

    public void sendMessage(Player player, String Message) {
        if (player != null && player.isOnline()) {
            player.sendMessage(Message);
        }
    }

    public boolean copyResource(Plugin plugin, String ressource, File destination) {
        InputStream template = plugin.getResource(ressource);
        if (template == null) {
            // 当 jar 中缺少该条目时（例如下载损坏、托管方重新打包或精简了 jar、构建不完整），
            // getResource() 会返回 null 而不是抛出异常。
            // 这里优雅地失败，避免在 onEnable 中抛出 NPE 导致 MyPet 被禁用。
            plugin.getLogger().warning("Could not copy bundled resource \"" + ressource + "\": it is missing from the MyPet jar.");
            return false;
        }
        try (InputStream in = template;
             OutputStream out = Files.newOutputStream(destination.toPath())) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public abstract TagCompound itemStackToCompund(ItemStack itemStack);

    public abstract ItemStack compundToItemStack(TagCompound compound);

    public abstract void sendMessageActionBar(Player player, String message);

    /**
     * 构建用于宠物血量更新的动作栏消息，以便所有 NMS 模块复用。
     * 该方法只负责构造消息；发送以及对条件限制（例如配置开关）的判断由调用方负责。
     */
    public String buildPetHealthActionBar(MyPet myPet, double health, double maxHealth) {
        if (myPet == null) {
            return "";
        }
        double deltaHealth = maxHealth - health;

        String healthColor = "§c"; // 红色
        if (health > maxHealth / 3 * 2) {
            healthColor = "§a"; // 绿色
        } else if (health > maxHealth / 3) {
            healthColor = "§e"; // 黄色
        }

        StringBuilder message = new StringBuilder();
        message.append(myPet.getPetName()).append("§r: ");

        if (health > 0) {
            message.append(healthColor)
                   .append(String.format("%1.2f", health))
                   .append("§f/")
                   .append(String.format("%1.2f", maxHealth))
                   .append(" ");

            if (!myPet.getOwner().isHealthBarActive()) {
                String deltaColor = deltaHealth < 0 ? "§a+" : "§c-";
                message.append("(")
                       .append(deltaColor)
                       .append(String.format("%1.2f", Math.abs(deltaHealth)))
                       .append("§r)");
            }
        } else {
            message.append(Translation.getString("Name.Dead", myPet.getOwner()));
        }

        return message.toString();
    }

    public abstract void addZombieTargetGoal(Zombie zombie);

    public abstract boolean comparePlayerWithEntity(MyPetPlayer player, Object obj);

    public abstract boolean isEquipment(ItemStack itemStack);

    public abstract String getVanillaName(ItemStack itemStack);

    public abstract void doPickupAnimation(Entity entity, Entity target);

    public abstract Entity getEntity(int id, World world);

    public double distanceSquared(Location a, Location b) {
        if (!a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        return a.distanceSquared(b);
    }

    public double distance(Location a, Location b) {
        if (!a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(distanceSquared(a, b));
    }

    public Material getMaterial(MaterialHolder materialHolder) {
        return Material.matchMaterial(materialHolder.getId().toUpperCase());
    }

    public boolean compareBlockPositions(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() &&
                a.getBlockY() == b.getBlockY() &&
                a.getBlockZ() == b.getBlockZ();
    }

    public boolean isSpigot() {
        try {
            Class.forName("org.spigotmc.SpigotConfig");
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public Entity getEntityByUUID(UUID uuid) {
        return Bukkit.getEntity(uuid);
    }

    public abstract String getLastDamageSource(LivingEntity e);

    public abstract String itemstackToString(ItemStack itemStack);

    public abstract boolean gameruleDoDeathMessages(LivingEntity e);

    public boolean doStackWalking(Class leClass, int oldDepth) {
        return false;
    }
}
