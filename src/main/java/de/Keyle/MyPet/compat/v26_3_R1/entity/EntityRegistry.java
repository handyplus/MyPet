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

package de.Keyle.MyPet.compat.v26_3_R1.entity;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.MyPetMinecraftEntity;
import de.Keyle.MyPet.api.entity.MyPetType;
import de.Keyle.MyPet.api.util.Compat;
import de.Keyle.MyPet.api.util.ReflectionUtil;
import lombok.SneakyThrows;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

@Compat("v26_3_R1")
public class EntityRegistry extends de.Keyle.MyPet.api.entity.EntityRegistry {

	BiMap<MyPetType, Class<? extends EntityMyPet>> entityClasses = HashBiMap.create();
	Map<MyPetType, EntityType> entityTypes = new HashMap<>();
	private DefaultedRegistry<EntityType> custReg = null;

	protected void registerEntityType(MyPetType petType, String key, DefaultedRegistry<EntityType<?>> entityRegistry) {
		EntityDimensions size = entityRegistry.get(Identifier.tryParse(key.toLowerCase())).get().value().getDimensions();
		EntityType leType;
		if(!entityRegistry.containsKey(Identifier.tryParse("mypet_" + key.toLowerCase()))) {
			leType = Registry.register(entityRegistry, "mypet_" + key.toLowerCase(), EntityType.Builder.createNothing(MobCategory.CREATURE).noSave().noSummon().sized(size.width(), size.height()).build(
					ResourceKey.create(Registries.ENTITY_TYPE, Identifier.parse(key))));
		} else {
			leType = entityRegistry.get(Identifier.tryParse("mypet_" + key.toLowerCase())).get().value();
		}
		entityTypes.put(petType, leType);
		EntityType<? extends LivingEntity> types = (EntityType<? extends LivingEntity>) entityRegistry.get(Identifier.tryParse(key)).get().value();
		registerDefaultAttributes(entityTypes.get(petType), types);
		overwriteEntityID(entityTypes.get(petType), getEntityTypeId(petType, entityRegistry), entityRegistry);
	}

	@SneakyThrows
	public static void registerDefaultAttributes(EntityType<? extends LivingEntity> customType, EntityType<? extends LivingEntity> rootType) {
		MyAttributeDefaults.registerCustomEntityType(customType, rootType);
	}

	protected void registerEntity(MyPetType type, DefaultedRegistry<EntityType<?>> entityRegistry) {
		Class<? extends EntityMyPet> entityClass = ReflectionUtil.getClass("de.Keyle.MyPet.compat.v26_3_R1.entity.types.EntityMy" + type.name());
		entityClasses.forcePut(type, entityClass);

		String key = type.getTypeID().toString();
		registerEntityType(type, key, entityRegistry);
	}

	public MyPetType getMyPetType(Class<? extends EntityMyPet> clazz) {
		return entityClasses.inverse().get(clazz);
	}

	@Override
	public MyPetMinecraftEntity createMinecraftEntity(MyPet pet, org.bukkit.World bukkitWorld) {
		EntityMyPet petEntity = null;
		Class<? extends MyPetMinecraftEntity> entityClass = entityClasses.get(pet.getPetType());
		Level world = ((CraftWorld) bukkitWorld).getHandle();

		try {
			Constructor<?> ctor = entityClass.getConstructor(Level.class, MyPet.class);
			Object obj = ctor.newInstance(world, pet);
			if (obj instanceof EntityMyPet) {
				petEntity = (EntityMyPet) obj;
			}
		} catch (Exception e) {
			MyPetApi.getLogger().info(ChatColor.RED + Util.getClassName(entityClass) + "(" + pet.getPetType() + ") is no valid MyPet(Entity)!");
			e.printStackTrace();
		}

		return petEntity;
	}

	@Override
	public boolean spawnMinecraftEntity(MyPetMinecraftEntity entity, org.bukkit.World bukkitWorld) {
		if (entity != null) {
			Level world = ((CraftWorld) bukkitWorld).getHandle();
			return world.addFreshEntity(((EntityMyPet) entity), CreatureSpawnEvent.SpawnReason.CUSTOM);
		}
		return false;
	}

	@Override
	public void registerEntityTypes() {
		//先准备原版注册表（Vanilla-Registry）
		DefaultedRegistry<EntityType<?>> entityRegistry = getRegistry(BuiltInRegistries.ENTITY_TYPE);
		Field frozenDoBe = ReflectionUtil.getField(MappedRegistry.class,"frozen");
		Field intrusiveHolderCacheField = ReflectionUtil.getField(MappedRegistry.class,"unregisteredIntrusiveHolders");
		Field allTagsField = ReflectionUtil.getField(MappedRegistry.class,"allTags");
		MethodHandle ENTITY_REGISTRY_SETTER = ReflectionUtil.createStaticFinalSetter(BuiltInRegistries.class, "ENTITY_TYPE");

		Object allTagsSaved = ReflectionUtil.getFieldValue(allTagsField, entityRegistry);

		if(custReg != null) {
			//得把原始注册表放回去，只是一小会儿
			try {
				ENTITY_REGISTRY_SETTER.invoke(entityRegistry);
			} catch (Throwable ignored) {
			}
		}

		//现在开始操作原版注册表
		Class TagSetClass = MappedRegistry.class.getDeclaredClasses()[0];
		Method unboundMethod = ReflectionUtil.getMethod(TagSetClass, "unbound");
		ReflectionUtil.setFinalFieldValue(frozenDoBe, entityRegistry, false);
		ReflectionUtil.setFinalFieldValue(intrusiveHolderCacheField, entityRegistry, new IdentityHashMap());
        try {
            ReflectionUtil.setFinalFieldValue(allTagsField, entityRegistry, unboundMethod.invoke(entityRegistry));
        } catch (Exception e) {
            e.printStackTrace();
        }

        //现在处理 Bukkit 注册表
		//先把旧注册表的 map 复制到一个新的 map 中：
		org.bukkit.Registry<org.bukkit.entity.EntityType> bukkitRegistry = org.bukkit.Registry.ENTITY_TYPE;
		Field mapField =  ReflectionUtil.getField(org.bukkit.Registry.SimpleRegistry.class, "map");
		Map<NamespacedKey, org.bukkit.entity.EntityType> bukkitMap = (Map) ReflectionUtil.getFieldValue(mapField, bukkitRegistry);
		ImmutableMap.Builder<NamespacedKey, org.bukkit.entity.EntityType> ownMap = ImmutableMap.builder();
		// 跳过遗留的 mypet_* 键，使重复注册（例如 /reload 之后）保持幂等，
		// 并且 ImmutableMap.Builder 不会因 "Multiple entries with same key" 而失败。
		bukkitMap.forEach((key, value) -> {
			if (!key.getKey().startsWith("mypet_")) {
				ownMap.put(key, value);
			}
		});

		for (MyPetType type : MyPetType.all()) {
			//有趣的部分来了
			registerEntity(type, entityRegistry);

			/*
			关于如何欺骗 Spigot 的教程：
				Spigot 现在不再回退到 "Unknown" 类型，而是不再接受 "null" 了
				（参见 https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/commits/02d49078870f39892e7e2ce2916e17a879e9a3f0#src/main/java/org/bukkit/craftbukkit/entity/CraftEntityType.java）
				这意味着 MyPet 实体（即 mypet_pig 等）无法被转换。
				这意味着 MyPet 在 1.20.1 末期使用的那个“hack”不再有效。
				而且仅在宠物生成时替换其类型也不行。
				这很糟糕。

				那么就来说说这个*小把戏*。
				基本上就是：
				我们直接告诉 Bukkit 我们是一个 BlockDisplay。没错。
				这意味着所有内容在开始时都会被正确创建，与其他插件交互时能被（大致）正确处理，
				宠物死亡时也一样。
				干这种事很蠢，但看起来能用 -> 我很开心。
			 */
			ownMap.put(NamespacedKey.fromString("mypet_" + type.getTypeID().toString()), org.bukkit.entity.EntityType.BLOCK_DISPLAY);
		}

		//后处理 Bukkit 注册表
		//现在我们的实体和其他所有实体都在里面了
		ReflectionUtil.setFieldValue(mapField, bukkitRegistry, ownMap.build());

		//后处理原版注册表
		entityRegistry.freeze();

		/* 解释一下这里发生了什么：
		之前我们保存了 allTags。它们包含诸如对附魔的特殊脆弱性、
		摔落伤害抗性，以及在某些情况下（例如海龟）生物在水下
		呼吸的能力等信息。
		通过上面的代码我们把那些标签重置了，所以现在必须做两件事：
		首先把标签重新加回去（这就是我们保存它们的原因）
		然后告诉 Minecraft 重新加载这些值，它们才能真正生效。
		真是爱死 MC 了 */
		ReflectionUtil.setFieldValue(allTagsField, entityRegistry, allTagsSaved);
		Method refreshMethod = ReflectionUtil.getMethod(MappedRegistry.class, "refreshTagsInHolders");
        try {
            refreshMethod.invoke(entityRegistry);
        } catch (Exception e) {
			e.printStackTrace();
        }

		if(custReg != null) {
			//得把自定义注册表放回原位
			try {
				ENTITY_REGISTRY_SETTER.invoke(custReg);
			} catch (Throwable ignored) {
			}
			custReg = null;
		}
	}

	public <T> T getEntityType(MyPetType petType) {
		return (T) this.entityTypes.get(petType);
	}

	@Override
	public void unregisterEntityTypes() {
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public DefaultedRegistry<EntityType<?>> getRegistry(DefaultedRegistry dMappedRegistry) {
		if (!dMappedRegistry.getClass().getName().equals(DefaultedMappedRegistry.class.getName())) {
			MyPetApi.getLogger().info("Custom entity registry found: " + dMappedRegistry.getClass().getName());
			if(custReg == null) {
				custReg = dMappedRegistry;
			}
			for (Field field : dMappedRegistry.getClass().getDeclaredFields()) {
				if (field.getType() == DefaultedMappedRegistry.class || field.getType() == MappedRegistry.class) {
					field.setAccessible(true);
					try {
						DefaultedRegistry<EntityType<?>> reg = (DefaultedRegistry<EntityType<?>>) field.get(dMappedRegistry);

						if (!reg.getClass().getName().equals(DefaultedMappedRegistry.class.getName())) {
							reg = getRegistry(reg);
						}
						return reg;
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return dMappedRegistry;
	}

	protected void overwriteEntityID(EntityType types, int id, DefaultedRegistry<EntityType<?>> entityRegistry) {
		try {
			Field bgF = MappedRegistry.class.getDeclaredField("toId");
			bgF.setAccessible(true);
			Object map = bgF.get(entityRegistry);
			Class<?> clazz = map.getClass();
			Method mapPut = clazz.getDeclaredMethod("put", Object.class, int.class);
			mapPut.setAccessible(true);
			mapPut.invoke(map, types, id);
		} catch (ReflectiveOperationException ex) {

			ex.printStackTrace();
		}

	}

	protected int getEntityTypeId(MyPetType type, DefaultedRegistry<EntityType<?>> entityRegistry) {
		EntityType<?> types = entityRegistry.get(Identifier.tryParse(type.getTypeID().toString())).get().value();
		return entityRegistry.getId(types);
	}
}
