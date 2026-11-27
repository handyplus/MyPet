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

package de.Keyle.MyPet.api.util;

import de.Keyle.MyPet.MyPetApi;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

/**
 * 跟踪 MyPet 打在非自己拥有的实体上的 Bukkit 元数据（宠物攻击过的怪物、
 * 用于远程捕捉而发射的投掷物）。Bukkit 只有在元数据被显式移除时才会清除它，
 * 因此那些没有死亡而是消失或卸载的实体上的元数据会一直留在服务器的
 * 元数据存储中。通过该类设置的实体会在消失一段时间后被周期性清理扫描清除。
 * <p>
 * 只能在主线程中使用。
 */
public class EntityMetadataTracker {

    private static final Map<UUID, TrackedEntity> TRACKED = new HashMap<>();

    private EntityMetadataTracker() {
    }

    public static void setMetadata(Entity entity, String key, Object value) {
        entity.setMetadata(key, new FixedMetadataValue(MyPetApi.getPlugin(), value));
        TrackedEntity tracked = TRACKED.get(entity.getUniqueId());
        if (tracked == null) {
            tracked = new TrackedEntity(entity);
            TRACKED.put(entity.getUniqueId(), tracked);
        } else {
            // 实体可能已被卸载并作为新对象重新加载
            tracked.entity = entity;
        }
        tracked.keys.add(key);
    }

    public static void removeMetadata(Entity entity, String key) {
        entity.removeMetadata(key, MyPetApi.getPlugin());
        TrackedEntity tracked = TRACKED.get(entity.getUniqueId());
        if (tracked != null) {
            tracked.keys.remove(key);
            if (tracked.keys.isEmpty()) {
                TRACKED.remove(entity.getUniqueId());
            }
        }
    }

    /**
     * 移除指定实体上所有被跟踪的元数据。
     */
    public static void removeAll(Entity entity) {
        TrackedEntity tracked = TRACKED.remove(entity.getUniqueId());
        if (tracked != null) {
            for (String key : tracked.keys) {
                entity.removeMetadata(key, MyPetApi.getPlugin());
            }
        }
    }

    /**
     * 某个实体连续多少次扫描都缺失后才会丢弃其元数据。扫描每分钟执行一次，
     * 因此仅仅是卸载的怪物会保留宠物对它做过的事情十分钟，以防其所属者回来。
     */
    private static final int SWEEPS_BEFORE_REMOVAL = 10;

    /**
     * 移除对已被跟踪、且连续 {@link #SWEEPS_BEFORE_REMOVAL} 次扫描都缺失（已死、已消失或已卸载）
     * 的实体的元数据。
     * <p>
     * Bukkit 按 UUID 存储实体元数据，因此卸载后再次加载的怪物会以一个仍然携带元数据的
     * 新对象出现。通过旧的、失效的对象移除元数据也会把元数据从活着的怪物身上移除，
     * 因此重新加载的怪物会按其 UUID 被重新取回。
     *
     * @return 被清理的实体数量
     */
    public static int sweep() {
        int removed = 0;
        Iterator<Map.Entry<UUID, TrackedEntity>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedEntity> entry = iterator.next();
            TrackedEntity tracked = entry.getValue();
            if (tracked.entity.isValid()) {
                tracked.missingSweeps = 0;
                continue;
            }
            Entity loaded = findLoaded(entry.getKey());
            if (loaded != null) {
                tracked.entity = loaded;
                tracked.missingSweeps = 0;
                continue;
            }
            if (++tracked.missingSweeps < SWEEPS_BEFORE_REMOVAL) {
                continue;
            }
            for (String key : tracked.keys) {
                tracked.entity.removeMetadata(key, MyPetApi.getPlugin());
            }
            iterator.remove();
            removed++;
        }
        return removed;
    }

    private static Entity findLoaded(UUID uuid) {
        try {
            Entity entity = MyPetApi.getPlatformHelper().getEntityByUUID(uuid);
            return entity != null && entity.isValid() ? entity : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static class TrackedEntity {
        Entity entity;
        int missingSweeps = 0;
        final Set<String> keys = new HashSet<>(2);

        TrackedEntity(Entity entity) {
            this.entity = entity;
        }
    }
}
