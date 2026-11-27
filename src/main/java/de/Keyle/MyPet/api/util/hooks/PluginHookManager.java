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

package de.Keyle.MyPet.api.util.hooks;

import com.google.common.collect.ArrayListMultimap;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.util.configuration.ConfigurationYAML;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * {@link PluginHookManager} 管理与其他插件的所有交互。钩子按类和它们所实现的接口存储，
 * 因此也可以通过这些接口获取。你可以获取其他插件的实例，并检查其他插件是否处于启用状态。
 */
public class PluginHookManager {

    protected ArrayListMultimap<Class<? extends PluginHook>, PluginHook> hooks = ArrayListMultimap.create();
    protected Map<String, PluginHook> hookByName = new HashMap<>();
    protected Map<Class<? extends PluginHook>, PluginHook> hookByClass = new HashMap<>();
    protected Queue<PluginHook> registeredHooks = new ArrayDeque<>();
    @Getter public ConfigurationYAML config;

    public PluginHookManager() {
        File hookConfigFile = new File(MyPetApi.getPlugin().getDataFolder().getPath() + File.separator + "hooks-config.yml");
        config = new ConfigurationYAML(hookConfigFile);
        config.getConfig().options().header("" +
                "#######################################################################\n" +
                "               This is the hook configuration of MyPet                #\n" +
                "                 You can find more info on the wiki:                  #\n" +
                "  https://wiki.mypet-plugin.de/setup/configurations/hooks-config.yml  #\n" +
                "#######################################################################\n");
        config.getConfig().options().copyHeader(true);
        config.getConfig().options().copyDefaults(true);
    }

    /**
     * 在这里注册新的钩子。钩子需要带有 {@link PluginHookName} 注解才会被接受。
     * MyPet 加载完成后注册的钩子不会被启用，请改用 enableHook。
     *
     * @param hookClass 钩子类
     */
    public void registerHook(Class<? extends PluginHook> hookClass) {
        if (hookClass.isAnnotationPresent(PluginHookName.class)) {
            PluginHookName hookNameAnnotation = hookClass.getAnnotation(PluginHookName.class);

            String pluginName = hookNameAnnotation.value();
            if (!hookNameAnnotation.classPath().equalsIgnoreCase("")) {
                if (!isPluginAvailable(pluginName, hookNameAnnotation.classPath())) {
                    return;
                }
            } else {
                if (!isPluginAvailable(pluginName)) {
                    return;
                }
            }
            try {
                PluginHook hook = hookClass.newInstance();
                registeredHooks.add(hook);
            } catch (Throwable e) {
                MyPetApi.getLogger().warning("Error occured while enabling " + pluginName + " (" + Bukkit.getPluginManager().getPlugin(pluginName).getDescription().getVersion() + ") hook.");
                e.printStackTrace();
            }
        }
    }

    /**
     * 为 MyPet 添加一个钩子。
     * 钩子需要带有 {@link PluginHookName} 注解才会被接受。
     *
     * @param hook 钩子
     * @return 钩子是否成功启用
     */
    @SuppressWarnings("unchecked")
    public boolean enableHook(PluginHook hook) {
        try {
            PluginHookName hookNameAnnotation = hook.getClass().getAnnotation(PluginHookName.class);
            if (!hookNameAnnotation.classPath().equalsIgnoreCase("")) {
                if (!isPluginUsable(hook.getPluginName(), hookNameAnnotation.classPath())) {
                    return false;
                }
            } else if (!isPluginUsable(hook.getPluginName())) {
                return false;
            }

            boolean enable = loadHookConfig(hook);
            config.saveConfig();

            if (enable && hook.onEnable()) {
                boolean genericHook = true;
                for (Object o : Util.getAllInterfaces(hook.getClass())) {
                    if (o != PluginHook.class && PluginHook.class.isAssignableFrom((Class) o)) {
                        hooks.put((Class) o, hook);
                        genericHook = false;
                    }
                }
                if (genericHook) {
                    hooks.put(PluginHook.class, hook);
                }
                hookByName.put(hook.getPluginName(), hook);
                hookByClass.put(hook.getClass(), hook);

                String message = hook.getPluginName();
                message += " (" + Bukkit.getPluginManager().getPlugin(hook.getPluginName()).getDescription().getVersion() + ")";
                if (!hookNameAnnotation.classPath().equalsIgnoreCase("")) {
                    message += " (" + hookNameAnnotation.classPath() + ")";
                }
                message += hook.getActivationMessage();
                MyPetApi.getLogger().info(message + " hook activated.");
                return true;
            }
        } catch (Throwable e) {
            MyPetApi.getLogger().warning("Error occured while enabling " + hook.getPluginName() + " (" + Bukkit.getPluginManager().getPlugin(hook.getPluginName()).getDescription().getVersion() + ") hook.");
            e.printStackTrace();
        }
        return false;
    }

    protected boolean loadHookConfig(PluginHook hook) {
        boolean enable = true;
        FileConfiguration config = this.config.getConfig();
        if (config.contains(hook.getPluginName())) {
            enable = config.getBoolean(hook.getPluginName() + ".Enabled", true);
        } else {
            config.addDefault(hook.getPluginName() + ".Enabled", true);
        }
        ConfigurationSection pluginSection = config.getConfigurationSection(hook.getPluginName());
        if (pluginSection != null) {
            hook.loadConfig(pluginSection);
        }

        return enable;
    }

    /**
     * 启用所有已注册的钩子。
     * MyPet 加载完成后无需调用。
     */
    public void enableHooks() {
        while (!registeredHooks.isEmpty()) {
            PluginHook hook = registeredHooks.poll();
            enableHook(hook);
        }
    }

    /**
     * 禁用所有已启用的钩子。
     * 仅用于禁用 MyPet。
     */
    public void disableHooks() {
        for (PluginHook hook : hooks.values()) {
            hook.onDisable();
        }
        hooks.clear();
        hookByName.clear();
        hookByClass.clear();
    }

    /**
     * 禁用某个指定的已启用钩子。
     *
     * @param hook 要禁用的钩子
     */
    public void disableHook(PluginHook hook) {
        hook.onDisable();
        for (Object o : Util.getAllInterfaces(hook.getClass())) {
            if (o != PluginHook.class && PluginHook.class.isAssignableFrom((Class) o)) {
                hooks.remove(o, hook);
            }
        }
        hooks.removeAll(hook.getClass());
        hookByName.remove(hook.getPluginName());
        hookByClass.remove(hook.getClass());
    }

    /**
     * 返回所有已启用的钩子
     *
     * @return 钩子类实例的列表
     */
    @SuppressWarnings("unchecked")
    public <T extends PluginHook> List<T> getHooks() {
        return (List<T>) new ArrayList<>(hookByName.values());
    }

    /**
     * 返回所有继承自指定类/接口的钩子
     *
     * @param hookClass 实现了 {@link PluginHook} 的类
     * @return 钩子类实例的列表
     */
    @SuppressWarnings("unchecked")
    public <T extends PluginHook> List<T> getHooks(Class<? extends T> hookClass) {
        return (List<T>) hooks.get(hookClass);
    }

    /**
     * 返回是否存在继承自指定类/接口的钩子
     *
     * @param hookClass 实现了 {@link PluginHook} 的类
     * @return 是否找到了任何钩子
     */
    public boolean hasHooks(Class<? extends PluginHook> hookClass) {
        return hooks.containsKey(hookClass);
    }

    /**
     * 返回指定类的钩子
     *
     * @param hookClass 实现了 {@link PluginHook} 的类
     * @return 钩子类的实例
     */
    @SuppressWarnings("unchecked")
    public <T extends PluginHook> T getHook(Class<? extends T> hookClass) {
        return (T) hookByClass.get(hookClass);
    }

    /**
     * 返回带有指定 {@link PluginHookName} 的钩子
     *
     * @param name 插件名称
     * @return 与该插件名称关联的钩子类实例
     */
    public PluginHook getHook(String name) {
        return hookByName.get(name);
    }

    /**
     * 返回带有指定 {@link PluginHookName} 的钩子是否可用
     *
     * @param name 插件名称
     * @return 是否找到了任何钩子
     */
    public boolean isHookActive(String name) {
        return hookByName.containsKey(name);
    }

    /**
     * 返回是否存在继承自指定类/接口的钩子
     *
     * @param hookClass 实现了 {@link PluginHook} 的类
     * @return 是否找到了任何钩子
     */
    public boolean isHookActive(Class<? extends PluginHook> hookClass) {
        return hookByClass.containsKey(hookClass);
    }

    /**
     * 查找插件实例
     *
     * @param clazz 插件的类
     * @return 插件的实例
     */
    public <T extends JavaPlugin> Optional<T> getPluginInstance(Class<T> clazz) {
        try {
            T plugin = JavaPlugin.getPlugin(clazz);
            if (plugin != null) {
                return Optional.of(plugin);
            }
        } catch (NoSuchMethodError e) {
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (clazz.isInstance(p)) {
                    T plugin = clazz.cast(p);
                    return Optional.of(plugin);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 查找插件实例
     *
     * @param pluginName 插件名称
     * @return 插件的实例
     */
    @SuppressWarnings("unchecked")
    public <T extends JavaPlugin> Optional<T> getPluginInstance(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.getName().equalsIgnoreCase(pluginName)) {
                    return Optional.of((T) p);
                }
            }
            return Optional.empty();
        }
        return Optional.of((T) plugin);
    }

    /**
     * 检查插件是否已启用
     *
     * @param pluginName 插件名称
     * @return 插件是否已启用
     */
    public boolean isPluginUsable(String pluginName) {
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    /**
     * 检查具有指定类名的插件是否已启用
     *
     * @param pluginName 插件名称
     * @param className  类名
     * @return 插件是否已启用
     */
    public static boolean isPluginUsable(String pluginName, String className) {
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled() && plugin.getClass().getName().equals(className);
    }

    /**
     * 检查插件是否可用
     *
     * @param pluginName 插件名称
     * @return 插件是否可用
     */
    public boolean isPluginAvailable(String pluginName) {
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null;
    }

    /**
     * 检查具有指定类名的插件是否可用
     *
     * @param pluginName 插件名称
     * @param className  类名
     * @return 插件是否可用
     */
    public static boolean isPluginAvailable(String pluginName, String className) {
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.getClass().getName().equals(className);
    }
}