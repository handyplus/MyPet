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

import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.util.hooks.PluginHookName;
import de.Keyle.MyPet.api.util.hooks.types.MountInsideHook;
import de.Keyle.MyPet.api.util.hooks.types.PlayerVersusEntityHook;
import de.Keyle.MyPet.api.util.hooks.types.PlayerVersusPlayerHook;
import org.bukkit.entity.*;

@PluginHookName("PlotSquared")
public class PlotSquaredHook implements PlayerVersusPlayerHook, PlayerVersusEntityHook, MountInsideHook {

    // 版本常量：0=无，3=V3，4=V4，6=V6+
    protected int version = 0;

    @Override
    public String getActivationMessage() {
        switch (version) {
            case 6:
                return " (V6+)";
            case 4:
                return " (V4)";
            case 3:
                return " (V3 Legacy)";
            default:
                return "";
        }
    }

    @Override
    public boolean onEnable() {
        // 先检查 V6+（v5 与 v6 使用相同的 API）
        try {
            Class.forName("com.plotsquared.core.plot.Plot");
            version = 6;
            return true;
        } catch (ClassNotFoundException ignored) {
        }

        // 检查 V4
        try {
            Class.forName("com.github.intellectualsites.plotsquared.plot.object.Plot");
            version = 4;
            return true;
        } catch (ClassNotFoundException ignored) {
        }

        // 检查 V3（旧版）
        try {
            Class.forName("com.intellectualcrafters.plot.flag.BooleanFlag");
            version = 3;
            return true;
        } catch (ClassNotFoundException ignored) {
        }

        return false;
    }

    @Override
    public boolean canHurt(Player attacker, Entity defender) {
        switch (version) {
            case 6:
                return V6.canHurt(attacker, defender);
            case 4:
                return V4.canHurt(attacker, defender);
            case 3:
                return V3.canHurt(attacker, defender);
            default:
                return true;
        }
    }

    @Override
    public boolean playerCanMount(MyPetPlayer player, Entity pet) {
        switch (version) {
            case 6:
                return V6.playerCanMount(player, pet);
            case 4:
            case 3:
            default:
                // V3 和 V4 未实现此检查
                return true;
        }
    }

    private static class V3 {

        public static boolean canHurt(Player attacker, Entity defender) {
            try {
                com.intellectualcrafters.plot.object.Location dloc = com.plotsquared.bukkit.util.BukkitUtil.getLocation(attacker);
                com.intellectualcrafters.plot.object.Location vloc = com.plotsquared.bukkit.util.BukkitUtil.getLocation(defender);
                com.intellectualcrafters.plot.object.PlotArea dArea = dloc.getPlotArea();
                com.intellectualcrafters.plot.object.PlotArea vArea = dArea != null && dArea.contains(vloc.getX(), vloc.getZ()) ? dArea : vloc.getPlotArea();
                if (dArea != null || vArea != null) {
                    com.intellectualcrafters.plot.object.Plot dplot = dArea != null ? dArea.getPlot(dloc) : null;
                    com.intellectualcrafters.plot.object.Plot vplot = vArea != null ? vArea.getPlot(vloc) : null;
                    com.intellectualcrafters.plot.object.Plot plot;
                    String stub;
                    if (dplot == null && vplot == null) {
                        if (dArea == null) {
                            return true;
                        }
                        plot = null;
                        stub = "road";
                    } else {
                        if (defender.getTicksLived() > attacker.getTicksLived()) {
                            if (dplot != null && defender instanceof Player) {
                                plot = dplot;
                            } else if (vplot == null) {
                                plot = dplot;
                            } else {
                                plot = vplot;
                            }
                        } else if (dplot != null && defender instanceof Player) {
                            plot = vplot == null ? dplot : vplot;
                        } else if (vplot == null) {
                            plot = dplot;
                        } else {
                            plot = vplot;
                        }
                        stub = plot.hasOwner() ? "other" : "unowned";
                    }

                    com.intellectualcrafters.plot.object.PlotPlayer plotPlayer1 = com.plotsquared.bukkit.util.BukkitUtil.getPlayer(attacker);
                    com.intellectualcrafters.plot.flag.BooleanFlag flag;
                    if (defender instanceof Hanging) {
                        flag = com.intellectualcrafters.plot.flag.Flags.HANGING_BREAK;
                        if (plot != null && (plot.getFlag(flag, Boolean.FALSE) ||
                                plot.isAdded(plotPlayer1.getUUID()))) {
                            return true;
                        }

                        if (!com.intellectualcrafters.plot.util.Permissions.hasPermission(plotPlayer1, "plots.admin.destroy." + stub)) {
                            return false;
                        }
                    } else if (defender.getEntityId() == 30) {
                        flag = com.intellectualcrafters.plot.flag.Flags.MISC_BREAK;
                        if (plot != null && (plot.getFlag(flag, Boolean.FALSE) ||
                                plot.isAdded(plotPlayer1.getUUID()))) {
                            return true;
                        }
                        if (!com.intellectualcrafters.plot.util.Permissions.hasPermission(plotPlayer1, "plots.admin.destroy." + stub)) {
                            return false;
                        }
                    } else if (!(defender instanceof Monster) && !(defender instanceof EnderDragon)) {
                        if (defender instanceof Tameable) {
                            if (plot != null) {
                                flag = com.intellectualcrafters.plot.flag.Flags.TAMED_ATTACK;
                                if (plot.getFlag(flag, Boolean.FALSE)) {
                                    return true;
                                }
                                flag = com.intellectualcrafters.plot.flag.Flags.PVE;
                                if (plot.getFlag(flag, Boolean.FALSE)) {
                                    return true;
                                }
                                if (plot.isAdded(plotPlayer1.getUUID())) {
                                    return true;
                                }
                            }
                            if (!com.intellectualcrafters.plot.util.Permissions.hasPermission(plotPlayer1, "plots.admin.pve." + stub)) {
                                return false;
                            }
                        } else if (defender instanceof Player) {
                            if (plot != null) {
                                flag = com.intellectualcrafters.plot.flag.Flags.PVP;
                                return !(flag.isFalse(plot) &&
                                        !com.intellectualcrafters.plot.util.Permissions.hasPermission(plotPlayer1, "plots.admin.pvp." + stub));
                            }
                            if (!com.intellectualcrafters.plot.util.Permissions.hasPermission(plotPlayer1, "plots.admin.pvp." + stub)) {
                                return false;
                            }
                        } else if (!(defender instanceof Creature)) {
                            if (defender instanceof Vehicle) {
                                return true;
                            }
                            flag = com.intellectualcrafters.plot.flag.Flags.PVE;
                            if (plot != null && (plot.getFlag(flag, Boolean.FALSE) ||
                                    plot.isAdded(plotPlayer1.getUUID()))) {
                                return true;
                            }
                            if (!com.intellectualcrafters.plot.util.Permissions.hasPermission(plotPlayer1, "plots.admin.pve." + stub)) {
                                return false;
                            }
                        } else {
                            if (plot != null) {
                                flag = com.intellectualcrafters.plot.flag.Flags.ANIMAL_ATTACK;
                                if (plot.getFlag(flag, Boolean.FALSE)) {
                                    return true;
                                }
                                flag = com.intellectualcrafters.plot.flag.Flags.PVE;
                                if (plot.getFlag(flag, Boolean.FALSE)) {
                                    return true;
                                }
                                if (plot.isAdded(plotPlayer1.getUUID())) {
                                    return true;
                                }
                            }
                            if (!com.intellectualcrafters.plot.util.Permissions.hasPermission(plotPlayer1, "plots.admin.pve." + stub)) {
                                return false;
                            }
                        }
                    } else {
                        if (plot != null) {
                            flag = com.intellectualcrafters.plot.flag.Flags.HOSTILE_ATTACK;
                            if (plot.getFlag(flag, Boolean.FALSE)) {
                                return true;
                            }
                            flag = com.intellectualcrafters.plot.flag.Flags.PVE;
                            if (plot.getFlag(flag, Boolean.FALSE)) {
                                return true;
                            }
                            if (plot.isAdded(plotPlayer1.getUUID())) {
                                return true;
                            }
                        }
                        if (!com.intellectualcrafters.plot.util.Permissions.hasPermission(plotPlayer1, "plots.admin.pve." + stub)) {
                            return false;
                        }
                    }
                }
            } catch (Throwable t) {
                return ProtectionHookErrors.failClosed(PlotSquaredHook.class, t);
            }
            return true;
        }
    }

    private static class V4 {

        public static boolean canHurt(Player attacker, Entity defender) {
            try {
                com.github.intellectualsites.plotsquared.plot.object.Location dloc = com.github.intellectualsites.plotsquared.bukkit.util.BukkitUtil.getLocation(attacker);
                com.github.intellectualsites.plotsquared.plot.object.Location vloc = com.github.intellectualsites.plotsquared.bukkit.util.BukkitUtil.getLocation(defender);
                com.github.intellectualsites.plotsquared.plot.object.PlotArea dArea = dloc.getPlotArea();
                com.github.intellectualsites.plotsquared.plot.object.PlotArea vArea = dArea != null && dArea.contains(vloc.getX(), vloc.getZ()) ? dArea : vloc.getPlotArea();
                if (dArea == null && vArea == null) {
                    return true;
                }

                com.github.intellectualsites.plotsquared.plot.object.Plot dplot = dArea != null ? dArea.getPlot(dloc) : null;
                com.github.intellectualsites.plotsquared.plot.object.Plot vplot = vArea != null ? vArea.getPlot(vloc) : null;

                com.github.intellectualsites.plotsquared.plot.object.Plot plot;
                String stub;
                if (dplot == null && vplot == null) {
                    if (dArea == null) {
                        return true;
                    }
                    plot = null;
                    stub = "road";
                } else {
                    // 优先使用地皮，以获得接近无缝的 PvP 区域
                    if (defender.getTicksLived() > attacker.getTicksLived()) {
                        if (dplot == null || !(defender instanceof Player)) {
                            plot = vplot == null ? dplot : vplot;
                        } else {
                            plot = dplot;
                        }
                    } else if (dplot == null || !(defender instanceof Player)) {
                        plot = vplot == null ? dplot : vplot;
                    } else if (vplot == null) {
                        plot = dplot;
                    } else {
                        plot = vplot;
                    }
                    stub = plot.hasOwner() ? "other" : "unowned";
                }

                com.github.intellectualsites.plotsquared.plot.object.PlotPlayer plotPlayer = com.github.intellectualsites.plotsquared.bukkit.util.BukkitUtil.getPlayer(attacker);
                if (defender instanceof Hanging) { // 悬挂实体
                    if (plot != null && (plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.HANGING_BREAK, false) ||
                            plot.isAdded(plotPlayer.getUUID()))) {
                        return true;
                    }
                    return com.github.intellectualsites.plotsquared.plot.util.Permissions.hasPermission(plotPlayer, "plots.admin.destroy." + stub);
                } else if (defender.getEntityId() == 30) {
                    if (plot != null && (plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.MISC_BREAK, false) ||
                            plot.isAdded(plotPlayer.getUUID()))) {
                        return true;
                    }
                    return com.github.intellectualsites.plotsquared.plot.util.Permissions.hasPermission(plotPlayer, "plots.admin.destroy." + stub);
                } else if (defender instanceof Monster || defender instanceof EnderDragon) { // 目标是怪物
                    if (plot != null && (plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.HOSTILE_ATTACK, false) ||
                            plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.PVE, false) ||
                            plot.isAdded(plotPlayer.getUUID()))) {
                        return true;
                    }
                    return com.github.intellectualsites.plotsquared.plot.util.Permissions.hasPermission(plotPlayer, "plots.admin.pve." + stub);
                } else if (defender instanceof Tameable) { // 目标是可驯服生物
                    if (plot != null && (plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.TAMED_ATTACK, false) ||
                            plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.PVE, false) ||
                            plot.isAdded(plotPlayer.getUUID()))) {
                        return true;
                    }
                    return com.github.intellectualsites.plotsquared.plot.util.Permissions.hasPermission(plotPlayer, "plots.admin.pve." + stub);
                } else if (defender instanceof Player) {
                    if (plot != null) {
                        return !com.github.intellectualsites.plotsquared.plot.flag.Flags.PVP.isFalse(plot) || com.github.intellectualsites.plotsquared.plot.util.Permissions.hasPermission(plotPlayer, "plots.admin.pvp." + stub);
                    }
                    return com.github.intellectualsites.plotsquared.plot.util.Permissions.hasPermission(plotPlayer, "plots.admin.pvp." + stub);
                } else if (defender instanceof Creature) { // 目标是动物
                    if (plot != null && (plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.ANIMAL_ATTACK, false) || plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.PVE, false) ||
                            plot.isAdded(plotPlayer.getUUID()))) {
                        return true;
                    }
                    return com.github.intellectualsites.plotsquared.plot.util.Permissions.hasPermission(plotPlayer, "plots.admin.pve." + stub);
                } else { // 目标是其它实体
                    if (plot != null && (plot.getFlag(com.github.intellectualsites.plotsquared.plot.flag.Flags.PVE, false) || plot.isAdded(plotPlayer.getUUID()))) {
                        return true;
                    }
                    return com.github.intellectualsites.plotsquared.plot.util.Permissions.hasPermission(plotPlayer, "plots.admin.pve." + stub);
                }
            } catch (Throwable t) {
                return ProtectionHookErrors.failClosed(PlotSquaredHook.class, t);
            }
        }
    }

    /**
     * V6 的实现使用反射，以避免与 V3 的 BukkitUtil 产生编译期冲突
     * （两者都使用 com.plotsquared.bukkit.util.BukkitUtil，但方法不同）
     */
    private static class V6 {

        private static Class<?> bukkitUtilClass;
        private static java.lang.reflect.Method adaptLocationMethod;
        private static java.lang.reflect.Method adaptPlayerMethod;

        static {
            try {
                bukkitUtilClass = Class.forName("com.plotsquared.bukkit.util.BukkitUtil");
                adaptLocationMethod = bukkitUtilClass.getMethod("adapt", org.bukkit.Location.class);
                adaptPlayerMethod = bukkitUtilClass.getMethod("adapt", org.bukkit.entity.Player.class);
            } catch (Exception ignored) {
            }
        }

        public static boolean canHurt(Player attacker, Entity defender) {
            try {
                // 使用反射调用 BukkitUtil.adapt()，以避免 V3/V6 类冲突
                Object dloc = adaptLocationMethod.invoke(null, attacker.getLocation());
                Object vloc = adaptLocationMethod.invoke(null, defender.getLocation());

                // 从位置获取 PlotArea
                java.lang.reflect.Method getPlotAreaMethod = dloc.getClass().getMethod("getPlotArea");
                java.lang.reflect.Method getXMethod = dloc.getClass().getMethod("getX");
                java.lang.reflect.Method getZMethod = dloc.getClass().getMethod("getZ");

                Object dArea = getPlotAreaMethod.invoke(dloc);
                Object vArea;
                if (dArea != null) {
                    java.lang.reflect.Method containsMethod = dArea.getClass().getMethod("contains", int.class, int.class);
                    boolean contains = (Boolean) containsMethod.invoke(dArea, getXMethod.invoke(vloc), getZMethod.invoke(vloc));
                    vArea = contains ? dArea : getPlotAreaMethod.invoke(vloc);
                } else {
                    vArea = getPlotAreaMethod.invoke(vloc);
                }

                if (dArea == null && vArea == null) {
                    return true;
                }

                // 获取地皮
                Object dplot = null;
                Object vplot = null;
                if (dArea != null) {
                    java.lang.reflect.Method getPlotMethod = dArea.getClass().getMethod("getPlot", dloc.getClass());
                    dplot = getPlotMethod.invoke(dArea, dloc);
                }
                if (vArea != null) {
                    java.lang.reflect.Method getPlotMethod = vArea.getClass().getMethod("getPlot", vloc.getClass());
                    vplot = getPlotMethod.invoke(vArea, vloc);
                }

                Object plot;
                String stub;
                if (dplot == null && vplot == null) {
                    if (dArea == null) {
                        return true;
                    }
                    plot = null;
                    stub = "road";
                } else {
                    // 优先使用地皮，以获得接近无缝的 PvP 区域
                    if (defender.getTicksLived() > attacker.getTicksLived()) {
                        if (dplot == null || !(defender instanceof Player)) {
                            plot = vplot == null ? dplot : vplot;
                        } else {
                            plot = dplot;
                        }
                    } else if (dplot == null || !(defender instanceof Player)) {
                        plot = vplot == null ? dplot : vplot;
                    } else if (vplot == null) {
                        plot = dplot;
                    } else {
                        plot = vplot;
                    }
                    java.lang.reflect.Method hasOwnerMethod = plot.getClass().getMethod("hasOwner");
                    stub = (Boolean) hasOwnerMethod.invoke(plot) ? "other" : "unowned";
                }

                // 通过反射获取地皮玩家
                Object plotPlayer = adaptPlayerMethod.invoke(null, attacker);
                java.lang.reflect.Method hasPermissionMethod = plotPlayer.getClass().getMethod("hasPermission", String.class);
                java.lang.reflect.Method getUUIDMethod = plotPlayer.getClass().getMethod("getUUID");
                java.util.UUID playerUUID = (java.util.UUID) getUUIDMethod.invoke(plotPlayer);

                // 检查标志与权限
                if (defender instanceof Hanging) {
                    if (plot != null && (checkBooleanFlag(plot, "HangingBreakFlag") || isAddedToPlot(plot, playerUUID))) {
                        return true;
                    }
                    return (Boolean) hasPermissionMethod.invoke(plotPlayer, "plots.admin.destroy." + stub);
                } else if (defender.getEntityId() == 30) {
                    if (plot != null && (checkBooleanFlag(plot, "MiscBreakFlag") || isAddedToPlot(plot, playerUUID))) {
                        return true;
                    }
                    return (Boolean) hasPermissionMethod.invoke(plotPlayer, "plots.admin.destroy." + stub);
                } else if (defender instanceof Monster || defender instanceof EnderDragon) {
                    if (plot != null && (checkBooleanFlag(plot, "HostileAttackFlag") || checkBooleanFlag(plot, "PveFlag") || isAddedToPlot(plot, playerUUID))) {
                        return true;
                    }
                    return (Boolean) hasPermissionMethod.invoke(plotPlayer, "plots.admin.pve." + stub);
                } else if (defender instanceof Tameable) {
                    if (plot != null && (checkBooleanFlag(plot, "TamedAttackFlag") || checkBooleanFlag(plot, "PveFlag") || isAddedToPlot(plot, playerUUID))) {
                        return true;
                    }
                    return (Boolean) hasPermissionMethod.invoke(plotPlayer, "plots.admin.pve." + stub);
                } else if (defender instanceof Player) {
                    if (plot != null) {
                        return checkBooleanFlag(plot, "PvpFlag") || (Boolean) hasPermissionMethod.invoke(plotPlayer, "plots.admin.pvp." + stub);
                    }
                    return (Boolean) hasPermissionMethod.invoke(plotPlayer, "plots.admin.pvp." + stub);
                } else if (defender instanceof Creature) {
                    if (plot != null && (checkBooleanFlag(plot, "AnimalAttackFlag") || checkBooleanFlag(plot, "PveFlag") || isAddedToPlot(plot, playerUUID))) {
                        return true;
                    }
                    return (Boolean) hasPermissionMethod.invoke(plotPlayer, "plots.admin.pve." + stub);
                } else {
                    if (plot != null && (checkBooleanFlag(plot, "PveFlag") || isAddedToPlot(plot, playerUUID))) {
                        return true;
                    }
                    return (Boolean) hasPermissionMethod.invoke(plotPlayer, "plots.admin.pve." + stub);
                }
            } catch (Throwable t) {
                return ProtectionHookErrors.failClosed(PlotSquaredHook.class, t);
            }
        }

        private static boolean checkBooleanFlag(Object plot, String flagName) {
            try {
                Class<?> flagClass = Class.forName("com.plotsquared.core.plot.flag.implementations." + flagName);
                java.lang.reflect.Method getFlagMethod = plot.getClass().getMethod("getFlag", Class.class);
                Object result = getFlagMethod.invoke(plot, flagClass);
                return result instanceof Boolean && (Boolean) result;
            } catch (Throwable ignored) {
            }
            return false;
        }

        private static boolean isAddedToPlot(Object plot, java.util.UUID uuid) {
            try {
                java.lang.reflect.Method isAddedMethod = plot.getClass().getMethod("isAdded", java.util.UUID.class);
                return (Boolean) isAddedMethod.invoke(plot, uuid);
            } catch (Throwable ignored) {
            }
            return false;
        }

        public static boolean playerCanMount(MyPetPlayer player, Entity pet) {
            try {
                Object loc = adaptLocationMethod.invoke(null, pet.getLocation());
                java.lang.reflect.Method getPlotAreaMethod = loc.getClass().getMethod("getPlotArea");
                Object area = getPlotAreaMethod.invoke(loc);

                if (area == null) {
                    return true;
                }

                java.lang.reflect.Method getPlotMethod = area.getClass().getMethod("getPlot", loc.getClass());
                Object plot = getPlotMethod.invoke(area, loc);

                if (plot == null) {
                    // 在道路上、不在任何地皮内——允许骑乘
                    return true;
                }

                // 检查玩家是否已被添加到该地皮（所有者、成员或受信任者）
                return isAddedToPlot(plot, player.getPlayerUUID());
            } catch (Throwable ignored) {
            }
            return true;
        }
    }

    @Override
    public boolean canHurt(Player attacker, Player defender) {
        return canHurt(attacker, (Entity) defender);
    }
}