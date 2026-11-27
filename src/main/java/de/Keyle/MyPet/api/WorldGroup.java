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

import de.Keyle.MyPet.api.util.configuration.ConfigurationYAML;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class WorldGroup {

    private static Map<String, WorldGroup> allGroups = new HashMap<>();
    private static Map<String, WorldGroup> groupWorlds = new HashMap<>();

    public static final WorldGroup DEFAULT_GROUP = new WorldGroup("default", false);
    public static final WorldGroup DISABLED_GROUP = new WorldGroup("---DISABLED---", true);

    private String name;
    @Getter private boolean disabled;
    private List<String> worlds = new ArrayList<>();


    public WorldGroup(String groupName, boolean disabled) {
        this.name = groupName.toLowerCase();
        this.disabled = disabled;
        if (allGroups.containsKey(this.getName())) {
            return;
        }
        allGroups.put(this.getName(), this);
    }

    public boolean addWorld(String world) {
        if (groupWorlds.containsKey(world)) {
            return false;
        }
        if (!this.worlds.contains(world)) {
            this.worlds.add(world);
            groupWorlds.put(world, this);
            return true;
        }
        return false;
    }

    public String getName() {
        return this.name;
    }

    public List<String> getWorlds() {
        return this.worlds;
    }

    @Override
    public String toString() {
        return "WorldGroup{name=" + name + ", worlds=" + worlds + "}";
    }

    /**
     * 检查某个世界组是否包含该世界
     *
     * @param worldName 被检查的世界名称
     * @return boolean
     */
    public boolean containsWorld(String worldName) {
        return this.worlds.contains(worldName);
    }

    /**
     * 返回所有可用的世界组
     *
     * @return Collection<WorldGroup>
     */
    public static Collection<WorldGroup> getGroups() {
        return Collections.unmodifiableCollection(allGroups.values());
    }

    /**
     * 返回该世界所属的世界组
     *
     * @param name 世界
     * @return WorldGroup
     */
    public static WorldGroup getGroupByWorld(String name) {
        WorldGroup group = groupWorlds.get(name);
        if (group == null) {
            return DISABLED_GROUP;
        }
        return group;
    }

    /**
     * 返回该世界所属的世界组
     *
     * @param world 世界
     * @return WorldGroup
     */
    public static WorldGroup getGroupByWorld(World world) {
        WorldGroup group = groupWorlds.get(world.getName());
        if (group == null) {
            return DISABLED_GROUP;
        }
        return group;
    }

    /**
     * 按名称返回世界组
     *
     * @param name 世界
     * @return WorldGroup
     */
    public static WorldGroup getGroupByName(String name) {
        WorldGroup group = allGroups.get(name);
        if (group == null) {
            return DISABLED_GROUP;
        }
        return group;
    }

    /**
     * 从各组中移除所有世界，然后删除这些组
     */
    public static void clearGroups() {
        allGroups.clear();
        groupWorlds.clear();
        allGroups.put(DEFAULT_GROUP.getName(), DEFAULT_GROUP);
        allGroups.put(DISABLED_GROUP.getName(), DISABLED_GROUP);
    }

    public static void loadGroups(File f) {
        ConfigurationYAML yamlConfiguration = new ConfigurationYAML(f);
        FileConfiguration config = yamlConfiguration.getConfig();

        WorldGroup.clearGroups();

        if (config == null) {
            return;
        }

        config.options().header("" +
                "######################################################################\n" +
                "          This is the world group configuration of MyPet             #\n" +
                "                You can find more info on the wiki:                  #\n" +
                "  https://wiki.mypet-plugin.de/setup/configurations/worldgroups.yml  #\n" +
                "######################################################################\n");
        config.options().copyHeader(true);

        Set<String> groups;
        Set<String> disabledWorlds = new HashSet<>();
        try {
            groups = config.getConfigurationSection("Groups").getKeys(false);
        } catch (NullPointerException e) {
            groups = new HashSet<>();
        }
        if (config.contains("Disabled")) {
            disabledWorlds.addAll(config.getStringList("Disabled"));
        } else {
            config.set("Disabled", new String[]{"example_world"});
            yamlConfiguration.saveConfig();
        }

        for (String world : disabledWorlds) {
            if (Bukkit.getServer().getWorld(world) != null) {
                WorldGroup.DISABLED_GROUP.addWorld(world);
            }
        }
        groups = groups.stream().filter(s -> !s.equals("default")).collect(Collectors.toSet());
        for (String node : groups) {
            List<String> worlds = config.getStringList("Groups." + node);
            if (!worlds.isEmpty()) {
                WorldGroup newGroup = new WorldGroup(node, false);
                for (String world : worlds) {
                    if (Bukkit.getServer().getWorld(world) != null) {
                        newGroup.addWorld(world);
                    }
                }
            }
        }

        List<String> worldNames = new ArrayList<>();
        boolean newWorldFound = false;
        List<String> worlds = config.getStringList("Groups." + DEFAULT_GROUP.name);
        if (!worlds.isEmpty()) {
            for (String world : worlds) {
                if (Bukkit.getServer().getWorld(world) != null) {
                    if (DEFAULT_GROUP.addWorld(world)) {
                        worldNames.add(world);
                    }
                }
            }
        }
        for (World world : Bukkit.getServer().getWorlds()) {
            if (WorldGroup.DEFAULT_GROUP.addWorld(world.getName())) {
                worldNames.add(world.getName());
                newWorldFound = true;
            }
        }
        if (newWorldFound && !worldNames.isEmpty()) {
            config.set("Groups.default", worldNames);
            yamlConfiguration.saveConfig();
        }
    }
}