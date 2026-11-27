package de.Keyle.MyPet.compat.v26_3_R1.util;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;

public final class VillagerNbtIO {
    private static HolderLookup.Provider lookupFor(org.bukkit.World world) {
        // Value I/O 所需的注册表访问
        RegistryAccess ra = ((CraftWorld) world).getHandle().registryAccess();
        return (HolderLookup.Provider) ra; // RegistryAccess 实现了 Provider
    }

    /** 读取 NBT 并应用到已有的村民或流浪商人 */
    public static void readInto(Entity bukkitEntity, CompoundTag tag) {
        try {
            net.minecraft.world.entity.Entity entity = ((CraftEntity) bukkitEntity).getHandle();
            net.minecraft.world.entity.npc.villager.AbstractVillager villager = (net.minecraft.world.entity.npc.villager.AbstractVillager) entity;

            HolderLookup.Provider lookup = lookupFor(bukkitEntity.getWorld());
            ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, lookup, tag);
            villager.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Could not read villager NBT", e);
        }
    }

    /** 从村民或流浪商人写出 NBT */
    public static CompoundTag writeFrom(Entity bukkitEntity) {
        try {
            net.minecraft.world.entity.Entity entity = ((CraftEntity) bukkitEntity).getHandle();
            net.minecraft.world.entity.npc.villager.AbstractVillager villager = (net.minecraft.world.entity.npc.villager.AbstractVillager) entity;

            HolderLookup.Provider lookup = lookupFor(bukkitEntity.getWorld());
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
            villager.saveWithoutId(output);
            return output.buildResult();
        } catch (Exception e) {
            throw new RuntimeException("Could not write villager NBT", e);
        }
    }
}
