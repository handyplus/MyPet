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

package de.Keyle.MyPet.compat.v26_3_R1.entity.types;

import com.mojang.datafixers.util.Pair;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.entity.EntitySize;
import de.Keyle.MyPet.api.entity.EquipmentSlot;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.types.MyHusk;
import de.Keyle.MyPet.compat.v26_3_R1.entity.EntityMyPet;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import de.Keyle.MyPet.compat.v26_3_R1.util.CompatItemStack;

import java.util.List;

import static de.Keyle.MyPet.compat.v26_3_R1.util.HandSlot.getSlotForHand;

@EntitySize(width = 0.6F, height = 1.9F)
public class EntityMyHusk extends EntityMyPet {

	private static final EntityDataAccessor<Boolean> AGE_WATCHER = SynchedEntityData.defineId(EntityMyHusk.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> TYPE_WATCHER = SynchedEntityData.defineId(EntityMyHusk.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> UNUSED_WATCHER = SynchedEntityData.defineId(EntityMyHusk.class, EntityDataSerializers.BOOLEAN);

	public EntityMyHusk(Level world, MyPet myPet) {
		super(world, myPet);
	}

	/**
	 * 返回 MyPet 死亡时播放的声音
	 */
	@Override
	protected String getMyPetDeathSound() {
		return "entity.husk.death";
	}

	/**
	 * 返回 MyPet 受伤时播放的声音
	 */
	@Override
	protected String getHurtSound() {
		return "entity.husk.hurt";
	}

	/**
	 * 返回 MyPet 的默认声音
	 */
	@Override
	protected String getLivingSound() {
		return "entity.husk.ambient";
	}

	/**
	 * 当玩家右键点击此 MyPet 时调用
	 * 返回：
	 * true：右键产生了反应
	 * false：右键没有产生反应
	 */

	@Override
	public InteractionResult handlePlayerInteraction(Player entityhuman, InteractionHand enumhand, ItemStack itemStack) {
		if (super.handlePlayerInteraction(entityhuman, enumhand, itemStack).consumesAction()) {
			return InteractionResult.CONSUME;
		}

		if (getOwner().equals(entityhuman) && itemStack != null) {
			if (itemStack.getItem() == Items.SHEARS && getOwner().getPlayer().isSneaking() && canEquip()) {
				boolean hadEquipment = false;
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					ItemStack itemInSlot = CraftItemStack.asNMSCopy(getMyPet().getEquipment(slot));
					if (itemInSlot != null && itemInSlot.getItem() != Items.AIR) {
						ItemEntity entityitem = new ItemEntity(this.level(), this.getX(), this.getY() + 1, this.getZ(), itemInSlot);
						entityitem.pickupDelay = 10;
						entityitem.setDeltaMovement(entityitem.getDeltaMovement().add(0, this.random.nextFloat() * 0.05F, 0));
						this.level().addFreshEntity(entityitem);
						getMyPet().setEquipment(slot, null);
						hadEquipment = true;
					}
				}
				if (hadEquipment) {
					if (itemStack != ItemStack.EMPTY && !entityhuman.getAbilities().instabuild) {
						try {
							itemStack.hurtAndBreak(1, entityhuman, getSlotForHand(enumhand));
						} catch (Error e) {
							// TODO 移除
						}
					}
				}
				return InteractionResult.CONSUME;
			} else if (MyPetApi.getPlatformHelper().isEquipment(CompatItemStack.asBukkitCopy(itemStack)) && getOwner().getPlayer().isSneaking() && canEquip()) {
				EquipmentSlot slot = EquipmentSlot.getSlotById(getEquipmentSlotForItem(itemStack).getId());
				ItemStack itemInSlot = CraftItemStack.asNMSCopy(getMyPet().getEquipment(slot));
				if (itemInSlot != null && itemInSlot.getItem() != Items.AIR && itemInSlot != ItemStack.EMPTY && !entityhuman.getAbilities().instabuild) {
					ItemEntity entityitem = new ItemEntity(this.level(), this.getX(), this.getY() + 1, this.getZ(), itemInSlot);
					entityitem.pickupDelay = 10;
					entityitem.setDeltaMovement(entityitem.getDeltaMovement().add(0, this.random.nextFloat() * 0.05F, 0));
					this.level().addFreshEntity(entityitem);
				}
				getMyPet().setEquipment(slot, CompatItemStack.asBukkitCopy(itemStack));
				if (itemStack != ItemStack.EMPTY && !entityhuman.getAbilities().instabuild) {
					itemStack.shrink(1);
					if (itemStack.getCount() <= 0) {
						entityhuman.getInventory().setItem(entityhuman.getInventory().getSelectedSlot(), ItemStack.EMPTY);
					}
				}
				return InteractionResult.CONSUME;
			} else if (Configuration.MyPet.Husk.GROW_UP_ITEM.compare(itemStack) && getMyPet().isBaby() && getOwner().getPlayer().isSneaking()) {
				if (itemStack != ItemStack.EMPTY && !entityhuman.getAbilities().instabuild) {
					itemStack.shrink(1);
					if (itemStack.getCount() <= 0) {
						entityhuman.getInventory().setItem(entityhuman.getInventory().getSelectedSlot(), ItemStack.EMPTY);
					}
				}
				getMyPet().setBaby(false);
				return InteractionResult.CONSUME;
			}
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(AGE_WATCHER, false);    // 是否宝宝
		builder.define(TYPE_WATCHER, 0);       // 类型
		builder.define(UNUSED_WATCHER, false);       // 未使用
	}

	@Override
	public void updateVisuals() {
		this.getEntityData().set(AGE_WATCHER, getMyPet().isBaby());

		Bukkit.getScheduler().runTaskLater(MyPetApi.getPlugin(), () -> {
			if (getMyPet().getStatus() == MyPet.PetState.Here) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					setPetEquipment(slot, CraftItemStack.asNMSCopy(getMyPet().getEquipment(slot)));
				}
			}
		}, 5L);
	}

	@Override
	public void playPetStepSound() {
		makeSound("entity.husk.step", 0.15F, 1.0F);
	}

	@Override
	public MyHusk getMyPet() {
		return (MyHusk) myPet;
	}

	public void setPetEquipment(EquipmentSlot slot, ItemStack itemStack) {
		((ServerLevel) this.level()).getChunkSource().sendToTrackingPlayersAndSelf(this, new ClientboundSetEquipmentPacket(getId(), List.of(new Pair<>(net.minecraft.world.entity.EquipmentSlot.values()[slot.get19Slot()], itemStack))));
	}

	@Override
	public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot vanillaSlot) {
		if (MyPetApi.getPlatformHelper().doStackWalking(ServerEntity.class, 2) && getMyPet() != null) {
			EquipmentSlot slot = EquipmentSlot.getSlotById(vanillaSlot.getId());
			if (getMyPet().getEquipment(slot) != null) {
				return CraftItemStack.asNMSCopy(getMyPet().getEquipment(slot));
			}
		}
		return super.getItemBySlot(vanillaSlot);
	}
}
