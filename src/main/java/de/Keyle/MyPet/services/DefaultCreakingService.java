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

package de.Keyle.MyPet.services;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.util.service.types.CreakingService;
import org.bukkit.Location;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.Entity;

/**
 * CreakingService 的默认实现。
 * 直接使用 Bukkit 的 Creaking API（1.21.4+ 可用）。
 * Creaking 类的引用被隔离在静态内部类中，以避免在低于 1.21.4 的服务器上
 * 出现类加载错误。
 */
public class DefaultCreakingService extends CreakingService {

    private final boolean supported;

    public DefaultCreakingService() {
        // 检查是否运行在存在 Creaking 的 1.21.4+ 版本上
        this.supported = MyPetApi.getCompatUtil().compareWithMinecraftVersion("1.21.4") >= 0;
    }

    @Override
    public Location getCreakingHome(Entity entity) {
        if (!supported) {
            return null;
        }
        // 委托给内部类以隔离 Creaking 类的引用
        return CreakingHelper.getHome(entity);
    }

    @Override
    public boolean isSupported() {
        return supported;
    }

    @Override
    public boolean onEnable() {
        return true;
    }

    @Override
    public void onDisable() {
    }

    /**
     * 引用 Creaking 类的内部类。
     * 该类仅在调用 getHome() 时才会被加载，而这只会在存在 Creaking 的
     * 1.21.4+ 服务器上发生。
     */
    private static class CreakingHelper {
        static Location getHome(Entity entity) {
            if (entity instanceof Creaking) {
                try { return ((Creaking) entity).getHome(); } catch (LinkageError ignored) { return null; } // 某些 1.21.4 构建中可能不存在 getHome()
            }
            return null;
        }
    }
}
