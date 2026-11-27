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

package de.Keyle.MyPet.api.util.service.types;

import de.Keyle.MyPet.api.util.service.ServiceContainer;
import de.Keyle.MyPet.api.util.service.ServiceName;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * 用于访问 Creaking 实体功能的服务。
 * 这层抽象允许在不使用反射的情况下提供各版本专用的实现。
 */
@ServiceName("CreakingService")
public abstract class CreakingService implements ServiceContainer {

    /**
     * 获取 Creaking 实体的归属位置（即 Creaking Heart 的位置）。
     *
     * @param entity 要获取归属位置的实体
     * @return 归属 Location；如果实体不是 Creaking 或未与 heart 关联，则返回 null
     */
    public abstract Location getCreakingHome(Entity entity);

    /**
     * 检查当前版本是否支持 Creaking Heart 捕捉。
     *
     * @return 如果支持 Creaking Heart 捕捉则返回 true
     */
    public abstract boolean isSupported();
}
