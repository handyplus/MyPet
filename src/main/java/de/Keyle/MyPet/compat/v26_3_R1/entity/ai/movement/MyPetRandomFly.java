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

package de.Keyle.MyPet.compat.v26_3_R1.entity.ai.movement;

import de.Keyle.MyPet.api.util.Compat;
import de.Keyle.MyPet.compat.v26_3_R1.entity.EntityMyPet;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * 让飞行宠物在静止的主人附近悬停并随机飞舞的 AI goal。
 *
 * <p>该 goal 继承 {@link MyPetRandomStroll}，为飞行宠物（蝙蝠、悦灵、蜜蜂等）提供专门的
 * 空中漫游行为。飞行宠物具有更高的漫游概率（8% 对 2%），从而形成更活跃的悬停行为。
 *
 * <p>与地面漫步的主要区别：
 * <ul>
 *   <li>通过 {@link HoverRandomPos} 和 {@link AirAndWaterRandomPos} 生成 3D 位置</li>
 *   <li>漫游目标同时支持水平与垂直半径</li>
 *   <li>更高的漫游概率（8%），使嗡嗡飞舞/悬停更活跃</li>
 *   <li>移动速度略高于地面宠物</li>
 * </ul>
 *
 * @see MyPetRandomStroll
 * @see MyPetRandomSwim
 */
@Compat("v26_3_R1")
public class MyPetRandomFly extends MyPetRandomStroll {

	// ==================== 可调常量 ====================
	// 这些值控制飞行宠物的随机悬停行为。
	// 除特别注明外，所有距离值单位为格，速度单位为格/tick。

	// -------------------------------------------------------------------------
	// 悬停激活 - 控制何时开始悬停的参数
	// -------------------------------------------------------------------------

	/**
	 * 条件满足时，每 tick 开始一次悬停的概率（0.0-1.0）。
	 * 取 0.08 表示每 tick 有 8% 概率 - 是地面宠物的 4 倍。
	 * 为飞行生物营造更活跃的嗡嗡飞舞/悬停行为。
	 */
	private static final float FLY_STROLL_CHANCE = 0.08F;

	// -------------------------------------------------------------------------
	// 悬停位置生成 - 空中目标选择的参数
	// -------------------------------------------------------------------------

	/**
	 * 主人静止时的水平悬停半径（格）。
	 * 飞行宠物使用较小半径，以便在主人附近更紧凑地悬停。
	 */
	private static final int FLY_WANDER_RADIUS_STATIONARY = 2;

	/**
	 * 主人移动时的水平悬停半径（格）。
	 * 跟随时允许更自由地选择位置。
	 */
	private static final int FLY_WANDER_RADIUS_MOVING = 4;

	/**
	 * 主人静止时的垂直悬停半径（格）。
	 * 控制空闲悬停时的高度变化。
	 */
	private static final int FLY_WANDER_VERTICAL_STATIONARY = 2;

	/**
	 * 主人移动时的垂直悬停半径（格）。
	 * 跟随时允许更大的垂直自由空间。
	 */
	private static final int FLY_WANDER_VERTICAL_MOVING = 4;

	/**
	 * 静止时，悬停目标距宠物当前位置的最大距离平方（格²）。
	 * 值 9.0 = 3 格。使悬停保持紧凑。
	 */
	private static final double FLY_WANDER_MAX_DIST_SQ_FROM_PET_STATIONARY = 9.0;

	/**
	 * 主人移动时，悬停目标距宠物当前位置的最大距离平方（格²）。
	 * 值 16.0 = 4 格。允许更远的悬停距离。
	 */
	private static final double FLY_WANDER_MAX_DIST_SQ_FROM_PET_MOVING = 16.0;

	/**
	 * 放弃前寻找有效悬停位置的最大尝试次数。
	 * 与地面宠物保持一致。
	 */
	private static final int FLY_WANDER_MAX_ATTEMPTS = 5;

	// -------------------------------------------------------------------------
	// 悬停速度 - 空中移动的参数
	// -------------------------------------------------------------------------

	/**
	 * 主人静止时的悬停移动速度（格/tick）。
	 * 缓慢、平静的悬停营造自然的空闲行为。
	 */
	private static final double FLY_HOVER_SPEED_STATIONARY = 0.2;

	/**
	 * 主人移动时的悬停移动速度（格/tick）。
	 * 稍快一些以便跟上，同时仍显得从容。
	 */
	private static final double FLY_HOVER_SPEED_MOVING = 0.3;

	/**
	 * 为指定的飞行宠物创建新的随机飞行 AI goal。
	 *
	 * @param petEntity     飞行宠物实体
	 * @param startDistance 飞行时与主人的最大距离（将由父类求平方）
	 */
	public MyPetRandomFly(EntityMyPet petEntity, int startDistance) {
		super(petEntity, startDistance);
		this.strollChance = FLY_STROLL_CHANCE;
	}

	/**
	 * 为飞行宠物生成一个随机的空中位置，供其朝该位置悬停。
	 *
	 * <p>以 {@link HoverRandomPos} 作为主要位置生成器，若悬停位置生成失败则回退到
	 * {@link AirAndWaterRandomPos}。该位置会被约束在主人与宠物当前位置的范围内。
	 *
	 * <p>当主人静止时，使用更小的半径（2 格 vs 4 格）以获得更紧凑的悬停行为。
	 *
	 * <p>位置相对于主人和宠物当前位置同时受限，保证两者都在可达范围内。
	 *
	 * @return 有效的空中漫游目标；若找不到合适位置则返回 {@code null}
	 */
	@Override
	protected Vec3 getPosition() {
		// ownerStationary 已由父类的 updateOwnerMovementTracking() 在 shouldStart() 中设置
		int horizontalRadius = ownerStationary ? FLY_WANDER_RADIUS_STATIONARY : FLY_WANDER_RADIUS_MOVING;
		int verticalRadius = ownerStationary ? FLY_WANDER_VERTICAL_STATIONARY : FLY_WANDER_VERTICAL_MOVING;
		double maxDistSqFromOwner = ownerStationary ? STATIONARY_MAX_DIST_SQ : (double) startDistance;
		double maxDistSqFromPet = ownerStationary ? FLY_WANDER_MAX_DIST_SQ_FROM_PET_STATIONARY : FLY_WANDER_MAX_DIST_SQ_FROM_PET_MOVING;

		Vec3 petPos = petEntity.position();
		Vec3 vec3d = this.petEntity.getViewVector(0.0F);

		for (int i = 0; i < FLY_WANDER_MAX_ATTEMPTS; i++) {
			Vec3 candidate = HoverRandomPos.getPos(this.petEntity, horizontalRadius, verticalRadius, vec3d.x, vec3d.z, 1.5707964F, 3, 1);
			if (candidate == null) {
				candidate = AirAndWaterRandomPos.getPos(this.petEntity, horizontalRadius, verticalRadius, -2, vec3d.x, vec3d.z, 1.5707963705062866D);
			}

			if (candidate != null) {
				double distSqToOwner = candidate.distanceToSqr(owner.getX(), owner.getY(), owner.getZ());
				double distSqToPet = candidate.distanceToSqr(petPos);

				if (distSqToOwner < maxDistSqFromOwner && distSqToPet < maxDistSqFromPet) {
					return candidate;
				}
			}
		}
		return null;
	}

	/**
	 * 应用空闲飞行时的移动速度。
	 *
	 * <p>使用平静、受控的速度，以防出现猛冲行为：
	 * <ul>
	 *   <li>主人静止：0.2（缓慢悬停）</li>
	 *   <li>主人移动：0.3（稍快）</li>
	 * </ul>
	 */
	@Override
	protected void applySpeed() {
		double walkSpeed = ownerStationary ? FLY_HOVER_SPEED_STATIONARY : FLY_HOVER_SPEED_MOVING;
		nav.getParameters().addSpeedModifier("RandomStroll", walkSpeed);
	}
}
