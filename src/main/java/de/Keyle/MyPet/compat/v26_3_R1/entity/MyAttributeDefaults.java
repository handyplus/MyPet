/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2025 Keyle
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

package de.Keyle.MyPet.compat.v26_3_R1.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.HashMap;

public class MyAttributeDefaults {

    private static final HashMap<EntityType<? extends LivingEntity>, AttributeSupplier> defaultAttribute = new HashMap<>();


    public static AttributeSupplier getAttribute(EntityType<?> types) {
        AttributeSupplier supplier = defaultAttribute.get(types);
        // 该 map 只保存自定义的 mypet_* 类型（在注册时填充）。对于底层的原版类型，
        // 通过 DefaultAttributes 从原版解析 supplier，而不是使用硬编码列表，
        // 这样新增或改名的生物都能被覆盖，且永远不会构造出 supplier 为 null 的 AttributeMap
        // （这会在应用装备属性修饰符时让 AttributeMap.getInstance 抛出 NPE）。
        if (supplier == null && DefaultAttributes.hasSupplier(types)) {
        	supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) types);
        }
        if (supplier == null) {
        	supplier = Mob.createMobAttributes().build();
        }
        return supplier;
    }

    public static void registerCustomEntityType(EntityType<? extends LivingEntity> customType, EntityType<? extends LivingEntity> rootType) {
        defaultAttribute.put(customType, getAttribute(rootType));
    }

}