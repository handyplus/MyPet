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

package de.Keyle.MyPet;

import de.Keyle.MyPet.api.PlatformHelper;
import de.Keyle.MyPet.api.entity.EntityRegistry;
import de.Keyle.MyPet.api.entity.MyPetInfo;
import de.Keyle.MyPet.api.entity.leashing.LeashFlagManager;
import de.Keyle.MyPet.api.plugin.MyPetPlugin;
import de.Keyle.MyPet.api.repository.MyPetManager;
import de.Keyle.MyPet.api.repository.PlayerManager;
import de.Keyle.MyPet.api.repository.Repository;
import de.Keyle.MyPet.api.skill.SkillManager;
import de.Keyle.MyPet.api.skill.skilltree.SkilltreeManager;
import de.Keyle.MyPet.api.util.CompatUtil;
import de.Keyle.MyPet.api.util.hooks.HookHelper;
import de.Keyle.MyPet.api.util.hooks.PluginHookManager;
import de.Keyle.MyPet.api.util.service.ServiceManager;

import java.util.logging.Logger;

public class MyPetApi {

    private static MyPetPlugin plugin;

    protected static void setPlugin(MyPetPlugin plugin) {
        if (MyPetApi.plugin != null) {
            return;
        }
        MyPetApi.plugin = plugin;
    }

    /**
     * @return 主插件实例
     */
    public static MyPetPlugin getPlugin() {
        return plugin;
    }

    /**
     * @return 存储宠物及其主人的存储库
     */
    public static Repository getRepository() {
        return plugin.getRepository();
    }

    /**
     * @return 插件日志记录器，或名为 MyPet 的日志记录器实例
     */
    public static Logger getLogger() {
        if (plugin != null) {
            return plugin.getLogger();
        } else {
            return Logger.getLogger("MyPet");
        }
    }

    /**
     * @return Bukkit 平台辅助类的实例
     */
    public static PlatformHelper getPlatformHelper() {
        return plugin.getPlatformHelper();
    }

    /**
     * @return 可在此获取宠物类型信息
     */
    public static MyPetInfo getMyPetInfo() {
        return plugin.getMyPetInfo();
    }

    /**
     * @return 在此注册和创建实体
     */
    public static EntityRegistry getEntityRegistry() {
        return plugin.getEntityRegistry();
    }

    /**
     * @return Bukkit 版本兼容管理器
     */
    public static CompatUtil getCompatUtil() {
        return plugin.getCompatUtil();
    }

    /**
     * @return MyPet 玩家管理器
     */
    public static PlayerManager getPlayerManager() {
        return plugin.getPlayerManager();
    }

    /**
     * @return MyPet 管理器
     */
    public static MyPetManager getMyPetManager() {
        return plugin.getMyPetManager();
    }

    /**
     * @return 可在此找到插件 Hook 辅助函数
     */
    public static HookHelper getHookHelper() {
        return plugin.getHookHelper();
    }

    /**
     * @return 插件 Hook 管理器的实例
     */
    public static PluginHookManager getPluginHookManager() {
        return plugin.getPluginHookManager();
    }

    /**
     * @return 插件 Hook 管理器的实例
     */
    public static ServiceManager getServiceManager() {
        return plugin.getServiceManager();
    }

    /**
     * @return 技能树管理器的实例
     */
    public static SkilltreeManager getSkilltreeManager() {
        return getServiceManager().getService(SkilltreeManager.class).get();
    }

    /**
     * @return 技能管理器的实例
     */
    public static SkillManager getSkillManager() {
        return getServiceManager().getService(SkillManager.class).get();
    }

    /**
     * @return 捕捉标志（LeashFlag）管理器的实例
     */
    public static LeashFlagManager getLeashFlagManager() {
        return getServiceManager().getService(LeashFlagManager.class).get();
    }
}