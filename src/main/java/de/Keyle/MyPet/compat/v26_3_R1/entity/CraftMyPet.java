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

import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.MyPetBukkitEntity;
import de.Keyle.MyPet.api.entity.MyPetType;
import de.Keyle.MyPet.api.entity.ai.target.TargetPriority;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.util.Compat;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.craftbukkit.inventory.CraftEntityEquipment;
import de.Keyle.MyPet.compat.v26_3_R1.util.CompatItemStack;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Compat("v26_3_R1")
public class CraftMyPet extends CraftMob implements MyPetBukkitEntity {

	protected MyPetPlayer petOwner;
	protected EntityMyPet petEntity;
	protected CraftEntityEquipment fakeEquipment;

	public CraftMyPet(CraftServer server, EntityMyPet entityMyPet) {
		super(server, entityMyPet);
		petEntity = entityMyPet;
		fakeEquipment = new FakeEquipment(this);
	}

	@Override
	public boolean canMove() {
		return petEntity.canMove();
	}


	@Override
	public void setSitting(boolean sitting) {
		getHandle().setSitting(sitting);
	}

	@Override
	public boolean isSitting() {
		return getHandle().isSitting();
	}

	@Override
	public boolean isInvisible() {
		return getHandle().isInvisible();
	}

	@Override
	public void setInvisible(boolean invisible) {
	}

	@Override
	public EntityMyPet getHandle() {
		return petEntity;
	}

	@Override
	public MyPet getMyPet() {
		return petEntity.getMyPet();
	}

	@Override
	public MyPetPlayer getOwner() {
		if (petOwner == null) {
			petOwner = getMyPet().getOwner();
		}
		return petOwner;
	}

	@Override
	public void removeEntity() {
		getHandle().discard();
	}

	@Override
	public MyPetType getPetType() {
		// 对于遗留的 Pet 实体，其 MyPet 已经不存在时 getMyPet() 会返回 null。
		de.Keyle.MyPet.api.entity.MyPet myPet = getMyPet();
		return myPet != null ? myPet.getPetType() : null;
	}

	/* 目前这样不行，因为 getType 被设为了 final……
		我看到其他插件是这么做的 - 这样应该没问题，还能解决与 p2 和 wg 的问题
	//更新 - 并不是！GriefPrevention 不喜欢之前的方案！
	//所以现在用这个丑陋的办法来解决它。但愿有效
	@Override
	public EntityType getType() {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		String class1 = stackTraceElements[2].getClassName();
		String class2 = stackTraceElements[3].getClassName();
		//所有特殊情况都在这里
		if(class1.contains("worldedit") || class2.contains("worldedit") ||
				class1.contains("plotsquared") || class2.contains("plotsquared") ||
				class1.contains("worldguard") || class2.contains("worldguard")  ||
				class1.contains("towny") || class2.contains("towny")) {
			return EntityType.valueOf(this.getPetType().getBukkitName());
		}

		return EntityType.UNKNOWN;
	} */

	/* 我完全不清楚为什么还需要重写这些其他的弃用方法，它们本来也不需要实现，所以就这样吧……*/
	@Override
	public void setVisibleByDefault(boolean b) {
	}

	@Override
	public boolean isVisibleByDefault() {
		return true;
	}

	@NotNull
	@Override
	public SpawnCategory getSpawnCategory() {
		return SpawnCategory.MISC;
	}

	@Override
	public boolean isInWater() {
		return getHandle().isInWater();
	}

	@Override
	public void remove() {
		// 什么都不做，以免其他插件移除这个 MyPet
		// 请使用 removeEntity() 来移除 MyPet
	}

	@Override
	public boolean isPersistent() {
		return false;
	}

	@Override
	public void setPersistent(boolean b) {
	}

	@Override
	public void damage(double v, @NotNull DamageSource damageSource) {

	}

	@Override
	public void setHealth(double health) {
		if (health < 0) {
			health = 0;
		}
		if (health > getMaxHealth()) {
			health = getMaxHealth();
		}
		super.setHealth(health);
	}

	@Override
	public int getArrowCooldown() {
		return 0;
	}

	@Override
	public void setArrowCooldown(int i) {

	}

	@Override
	public int getArrowsInBody() {
		return 0;
	}

	@Override
	public void setArrowsInBody(int i) {

	}

	@Override
	public EntityEquipment getEquipment() {
		return fakeEquipment;
	}

	@Override
	public void setRiptiding(boolean b) {
	}

	@Override
	public void attack(@NotNull Entity entity) {
		this.petEntity.attack(((CraftEntity) entity).getHandle());
	}

	@Override
	public void swingMainHand() {
	}

	@Override
	public void swingOffHand() {
	}

	@Override
	public void setTarget(LivingEntity target) {
		setTarget(target, TargetPriority.Bukkit);
	}

	@Override
	public void setAware(boolean b) {
	}

	@Override
	public boolean isAware() {
		return true;
	}

	@Override
	public void forgetTarget() {
		getHandle().forgetTarget();
	}

	@Override
	public void setTarget(LivingEntity target, TargetPriority priority) {
		getHandle().setMyPetTarget(target, priority);
	}

	@Override
	public String toString() {
		return "CraftMyPet{MyPet=" + getHandle().isMyPet() + ",owner=" + getOwner() + ",type=" + getPetType() + "}";
	}

	private class FakeEquipment extends CraftEntityEquipment {

		public FakeEquipment(CraftLivingEntity entity) {
			super(entity);
		}

		@NotNull
		@Override
		public ItemStack getItemInMainHand() {
			return CompatItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
		}

		@NotNull
		@Override
		public ItemStack getItemInOffHand() {
			return CompatItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
		}

		@NotNull
		@Override
		public ItemStack getItemInHand() {
			return CompatItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
		}

		@Override
		public ItemStack getHelmet() {
			return CompatItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
		}

		@Override
		public ItemStack getChestplate() {
			return CompatItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
		}

		@Override
		public ItemStack getLeggings() {
			return CompatItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
		}

		@Override
		public ItemStack getBoots() {
			return CompatItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
		}

		@NotNull
		@Override
		public ItemStack[] getArmorContents() {
			ItemStack empty = CompatItemStack.asBukkitCopy(net.minecraft.world.item.ItemStack.EMPTY);
			return new ItemStack[]{empty, empty, empty, empty};
		}
	}
}
