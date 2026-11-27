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

package de.Keyle.MyPet.util.hooks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.entity.MyPetBukkitEntity;
import de.Keyle.MyPet.api.entity.MyPetBukkitPart;
import de.Keyle.MyPet.api.entity.MyPetType;
import de.Keyle.MyPet.api.util.ReflectionUtil;
import de.Keyle.MyPet.api.util.hooks.PluginHook;
import de.Keyle.MyPet.api.util.hooks.PluginHookName;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@PluginHookName("ProtocolLib")
public class ProtocolLibHook implements PluginHook {

    protected boolean checkTemporaryPlayers = false;
    private Set<Player> tempBlockedPlayers = ConcurrentHashMap.newKeySet();
    @Override
    public boolean onEnable() {
        try {
            // 下面所有监听器只用于修复末影龙宠物（龙的各个部件以及朝向旋转）。
            // 末影龙宠物在 1.21.4+ 上无法激活，因此那里没有需要修复的内容，
            // 而监听每一个实体移动数据包只会白白消耗性能。
            if (MyPetApi.getCompatUtil().compareWithMinecraftVersion("1.21.4") >= 0) {
                return true;
            }

        	if(MyPetApi.getCompatUtil().compareWithMinecraftVersion("1.17") < 0) {
        		// 这是异步的
        		registerEnderDragonInteractionFix();
        	} else {
                // 这不是异步的——1.17+ 不喜欢异步操作
                registerSyncEnderDragonInteractionFix();
            }

            checkTemporaryPlayers = ReflectionUtil.getMethod(PacketEvent.class, "isPlayerTemporary") != null;

            // 反转龙的朝向
            if (MyPetApi.getCompatUtil().compareWithMinecraftVersion("1.9") >= 0) {
                registerEnderDragonRotationFix19();
            } else {
                registerEnderDragonRotationFixLegacy();
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        try {
            if (ProtocolLibrary.getProtocolManager() != null) {
                ProtocolLibrary.getProtocolManager().removePacketListeners(MyPetApi.getPlugin());
            }
        } catch (Exception ignored) {
        }
    }

    private void registerEnderDragonInteractionFix() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(MyPetApi.getPlugin(), PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {

                if ((checkTemporaryPlayers && event.isPlayerTemporary()) || event.isCancelled()) {
                    return;
                }
                PacketContainer packet = event.getPacket();
                if (packet.getType() == PacketType.Play.Client.USE_ENTITY) {

                    int id = packet.getIntegers().read(0);

                    Entity entity = null;
                    try {
                        entity = packet.getEntityModifier(event).readSafely(0);
                    } catch (RuntimeException ignored) {
                    }
                    if (entity == null && event.getPlayer() != null) {
                        entity = MyPetApi.getPlatformHelper().getEntity(id, event.getPlayer().getWorld());
                    }
                    if (entity instanceof MyPetBukkitPart) {
                        entity = ((MyPetBukkitPart) entity).getPetOwner();
                        packet.getIntegers().write(0, entity.getEntityId());
                    }
                }
            }
        });
    }

    private void registerSyncEnderDragonInteractionFix() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(MyPetApi.getPlugin(), PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {

                if ((checkTemporaryPlayers && event.isPlayerTemporary()) || event.isCancelled()) {
                    return;
                }

                // 防止点击刷屏导致整个服务器出现网络问题。本质上是一种低成本限流
                if (tempBlockedPlayers.contains(event.getPlayer())) {
                    return;
                } else {
                    tempBlockedPlayers.add(event.getPlayer());
                    // 注册限流清理任务
                    Bukkit.getScheduler().runTaskLaterAsynchronously(MyPetApi.getPlugin(), () -> tempBlockedPlayers.remove(event.getPlayer()), 2L);
                }

                PacketContainer packet = event.getPacket();
                if (packet.getType() == PacketType.Play.Client.USE_ENTITY) {
                    try {
                        Entity ent = ensureMainThread(() -> {
                            int id = packet.getIntegers().read(0);

                            Entity entity = null;
                            try {
                                entity = packet.getEntityModifier(event).readSafely(0);
                            } catch (RuntimeException ignored) {
                            }
                            if (entity == null && event.getPlayer() != null) {
                                entity = MyPetApi.getPlatformHelper().getEntity(id, event.getPlayer().getWorld());
                            }
                            if (entity instanceof MyPetBukkitPart) {
                                entity = ((MyPetBukkitPart) entity).getPetOwner();
                            }
                            return entity;
                        });
                        if(ent != null) {
                            packet.getIntegers().write(0, ent.getEntityId());
                        }
                    } catch (TimeoutException e) {
                        // 假定主线程已被阻塞，应当释放这个 netty 线程。
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private <T> T ensureMainThread(Supplier<T> supplier) throws ExecutionException, InterruptedException, TimeoutException {
        if(Bukkit.isPrimaryThread()) {
            return supplier.get();
        } else {
            return Bukkit.getServer().getScheduler().callSyncMethod(MyPetApi.getPlugin(), supplier::get)
                .get(100, TimeUnit.MILLISECONDS);
        }
    }

    protected List<PacketType> getFixedPackets() {
        List<PacketType> types = new ArrayList<>();
        for (PacketType pt : PacketType.Play.Server.getInstance().values()) {
            switch (pt.name()) {
                case "ENTITY_LOOK":
                case "ENTITY_MOVE_LOOK":
                case "REL_ENTITY_MOVE_LOOK":
                case "ENTITY_HEAD_ROTATION":
                case "ENTITY_TELEPORT":
                    if (pt.isSupported()) {
                        types.add(pt);
                    }
            }
        }
        return types;
    }

    private void registerEnderDragonRotationFix19() {
        // 缓存版本检查结果——它在运行期间不会改变
        final boolean isAtLeast1_17 = MyPetApi.getCompatUtil().compareWithMinecraftVersion("1.17") >= 0;

        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(MyPetApi.getPlugin(), getFixedPackets()) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        if ((checkTemporaryPlayers && event.isPlayerTemporary()) || event.isCancelled()) {
                            return;
                        }

                        PacketContainer packet = event.getPacket();
                        int id = packet.getIntegers().read(0);

                        Entity entity = null;
                        if (!isAtLeast1_17) {
                            try {
                               entity = packet.getEntityModifier(event).readSafely(0);
                            } catch (RuntimeException ignored) {
                            }
                        }
                        if (entity == null) {
                            if (isAtLeast1_17) {
                                try {
                                    entity = ensureMainThread(() -> MyPetApi.getPlatformHelper().getEntity(id, event.getPlayer().getWorld()));
                                } catch (TimeoutException e) {
                                    // 假定主线程已被阻塞，应当释放这个 netty 线程。
                                    return;
                                }  catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                entity = MyPetApi.getPlatformHelper().getEntity(id, event.getPlayer().getWorld());
                            }
                        }

                        if (entity instanceof MyPetBukkitEntity && ((MyPetBukkitEntity) entity).getPetType() == MyPetType.EnderDragon) {
                            byte angle = packet.getBytes().read(0);
                            angle += Byte.MAX_VALUE;
                            packet.getBytes().write(0, angle);
                        }
                    }
                });
    }

    private void registerEnderDragonRotationFixLegacy() {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(MyPetApi.getPlugin(), getFixedPackets()) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        if ((checkTemporaryPlayers && event.isPlayerTemporary()) || event.isCancelled()) {
                            return;
                        }

                        PacketContainer packet = event.getPacket();

                        Entity entity = null;
                        try {
                            entity = packet.getEntityModifier(event).readSafely(0);
                        } catch (RuntimeException ignored) {
                        }

                        if (entity instanceof MyPetBukkitEntity && ((MyPetBukkitEntity) entity).getPetType() == MyPetType.EnderDragon) {

                            switch (packet.getType().name()) {
                                case "ENTITY_TELEPORT":
                                case "ENTITY_HEAD_ROTATION": {
                                    byte angle = packet.getBytes().read(0);
                                    angle += Byte.MAX_VALUE;
                                    packet.getBytes().write(0, angle);
                                    break;
                                }
                                case "ENTITY_LOOK":
                                case "ENTITY_MOVE_LOOK":
                                case "REL_ENTITY_MOVE_LOOK":
                                case "VEHICLE_MOVE":
                                    byte angle = packet.getBytes().read(3);
                                    angle += Byte.MAX_VALUE;
                                    packet.getBytes().write(3, angle);
                                    break;
                            }
                        }
                    }
                });
    }
}
