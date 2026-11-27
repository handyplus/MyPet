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
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

/**
 * AI goal：让水生宠物在静止的主人附近随机游动。
 *
 * <p>该 goal 继承 {@link MyPetRandomStroll}，为水生宠物（鱼、海豚、美西螈等）提供专门的
 * 水下漫步行为。水生宠物的漫步概率适中（6% 对 2%），并使用可在水中寻路的位置生成方式。
 *
 * <p>与地面漫步的主要区别：
 * <ul>
 *   <li>当宠物与主人都在水中时，使用可在水中寻路的位置生成方式</li>
 *   <li>更大的漫步半径（水平 3-10 格、垂直 2-7 格），使游动更自然</li>
 *   <li>更高的漫步概率（6%），游动更活跃</li>
 *   <li>更快的速度（0.25-0.35），以抵消水的阻力</li>
 *   <li>宠物不在水中时回退到地面漫步</li>
 * </ul>
 *
 * @see MyPetRandomStroll
 * @see MyPetRandomFly
 */
@Compat("v26_3_R1")
public class MyPetRandomSwim extends MyPetRandomStroll {

	// ==================== 可调常量 ====================
	// 这些数值控制水生宠物的随机游动行为。
	// 除非另行说明，所有距离值单位为格，速度单位为格/tick。

	// -------------------------------------------------------------------------
	// 游动激活 - 控制游动何时开始的参数
	// -------------------------------------------------------------------------

	/**
	 * 条件满足时，每 tick 开始一次游动的概率（0.0-1.0）。
	 * 取 0.06 表示每 tick 有 6% 概率 - 是地面宠物的 3 倍。
	 * 营造适合水生生物的适度活跃游动。
	 */
	private static final float SWIM_STROLL_CHANCE = 0.06F;

	// -------------------------------------------------------------------------
	// 游动位置生成 - 水下目标选择的参数
	// -------------------------------------------------------------------------

	/**
	 * 主人静止时的水平游动半径（格）。
	 * 水生宠物会使用比飞行宠物稍大的半径，因为
	 * 水域环境通常更为开阔。
	 */
	private static final int SWIM_WANDER_RADIUS_STATIONARY = 3;

	/**
	 * 主人移动时的水平游动半径（格）。
	 * 较大的半径（10 格）允许自然的长距离游动。
	 */
	private static final int SWIM_WANDER_RADIUS_MOVING = 10;

	/**
	 * 主人静止时的垂直游动半径（格）。
	 * 控制空闲游动时的深度变化。
	 */
	private static final int SWIM_WANDER_VERTICAL_STATIONARY = 2;

	/**
	 * 主人移动时的垂直游动半径（格）。
	 * 较大的半径（7 格）允许自然的水深变化。
	 */
	private static final int SWIM_WANDER_VERTICAL_MOVING = 7;

	/**
	 * 静止时，游动目标距宠物当前位置的最大距离平方（格²）。
	 * 值 9.0 = 3 格。使游动保持在有限范围内。
	 */
	private static final double SWIM_WANDER_MAX_DIST_SQ_FROM_PET_STATIONARY = 9.0;

	/**
	 * 主人移动时，游动目标距宠物当前位置的最大距离平方（格²）。
	 * 值 16.0 = 4 格。允许更长的游动距离。
	 */
	private static final double SWIM_WANDER_MAX_DIST_SQ_FROM_PET_MOVING = 16.0;

	/**
	 * 放弃前寻找有效游动位置的最大尝试次数。
	 * 为保持一致，与其他宠物类型相同。
	 */
	private static final int SWIM_WANDER_MAX_ATTEMPTS = 5;

	/**
	 * {@link #makeWaterPos} 中水位置验证的最大迭代次数。
	 * 比漫步尝试次数更高，因为水中寻路的限制更多。
	 */
	private static final int WATER_POS_MAX_ITERATIONS = 10;

	// -------------------------------------------------------------------------
	// 游动速度 - 水下移动的参数
	// -------------------------------------------------------------------------

	/**
	 * 主人静止时的游动速度（格/tick）。
	 * 高于地面宠物（0.15），以抵消水的阻力。
	 */
	private static final double SWIM_SPEED_STATIONARY = 0.25;

	/**
	 * 主人移动时的游动速度（格/tick）。
	 * 高于静止时的速度以便跟上主人，但依然放松。
	 */
	private static final double SWIM_SPEED_MOVING = 0.35;

	/**
	 * 为指定水生宠物创建新的随机游动 AI goal。
	 *
	 * @param petEntity     水生宠物实体
	 * @param startDistance 游动时与主人的最大距离（父类会对其求平方）
	 */
	public MyPetRandomSwim(EntityMyPet petEntity, int startDistance) {
		super(petEntity, startDistance);
		this.strollChance = SWIM_STROLL_CHANCE;
	}

	/**
	 * 为水生宠物生成一个随机水下位置作为游动目标。
	 *
	 * <p>当宠物与主人都在水中时，使用专门的水位置生成方式，
	 * 并采用更大的半径（主人移动时水平 3-10 格、垂直 2-7 格）。
	 * 不在水中时回退到父类的地面位置生成方式。
	 *
	 * @return 有效的游动目标位置；若找不到合适位置则返回 {@code null}
	 */
	@Override
	protected Vec3 getPosition() {
		boolean petInWater = petEntity.isInWater() || petEntity.getInBlockState().is(Blocks.BUBBLE_COLUMN);
		boolean ownerInWater = owner.isInWater() || owner.getInBlockState().is(Blocks.BUBBLE_COLUMN);

		// ownerStationary 已由父类在 shouldStart() 中的 updateOwnerMovementTracking() 设置
		int horizontalRadius = ownerStationary ? SWIM_WANDER_RADIUS_STATIONARY : SWIM_WANDER_RADIUS_MOVING;
		int verticalRadius = ownerStationary ? SWIM_WANDER_VERTICAL_STATIONARY : SWIM_WANDER_VERTICAL_MOVING;
		double maxDistSqFromOwner = ownerStationary ? STATIONARY_MAX_DIST_SQ : (double) startDistance;
		double maxDistSqFromPet = ownerStationary ? SWIM_WANDER_MAX_DIST_SQ_FROM_PET_STATIONARY : SWIM_WANDER_MAX_DIST_SQ_FROM_PET_MOVING;

		Vec3 petPos = petEntity.position();

		if (petInWater && ownerInWater) {
			for (int i = 0; i < SWIM_WANDER_MAX_ATTEMPTS; i++) {
				Vec3 candidate = makeWaterPos(this.petEntity, horizontalRadius, verticalRadius);
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
		return super.getPosition();
	}

	/**
	 * 生成一个可在水中寻路的游动位置。
	 *
	 * <p>最多迭代 10 次，以寻找一个可通过水寻路且其上方有液体的位置
	 * （确保宠物确实能游到那里）。
	 *
	 * @param mob              要为其生成位置的寻路生物
	 * @param horizontalRadius 水平搜索半径
	 * @param verticalRadius   垂直搜索半径
	 * @return 可在水中寻路的位置；否则返回最后生成的位置
	 */
	private Vec3 makeWaterPos(PathfinderMob mob, int horizontalRadius, int verticalRadius) {
		Vec3 position = generateRandomPosition(mob, horizontalRadius, verticalRadius);
		if (position == null) {
			return null;
		}

		for (int attempt = 0; attempt < WATER_POS_MAX_ITERATIONS; attempt++) {
			BlockPos blockPos = new BlockPos((int) position.x, (int) position.y, (int) position.z);
			BlockPos abovePos = blockPos.above();

			boolean isWaterPathfindable = mob.level().getBlockState(blockPos).isPathfindable(PathComputationType.WATER);
            boolean hasLiquidAbove = !mob.level().getFluidState(abovePos).isEmpty();

			if (isWaterPathfindable || hasLiquidAbove) {
				return position;
			}

			position = generateRandomPosition(mob, horizontalRadius, verticalRadius);
			if (position == null) {
				return null;
			}
		}

		return position;
	}

	/**
	 * 使用 NMS 的随机位置工具生成一个随机位置。
	 *
	 * @param mob              寻路生物
	 * @param horizontalRadius 水平搜索半径
	 * @param verticalRadius   垂直搜索半径
	 * @return 随机生成的位置向量
	 */
	private static Vec3 generateRandomPosition(PathfinderMob mob, int horizontalRadius, int verticalRadius) {
		return RandomPos.generateRandomPos(mob, () -> {
			BlockPos blockPos = RandomPos.generateRandomDirection(mob.getRandom(), horizontalRadius, verticalRadius);
			return generateRandomPosTowardDirection(mob, horizontalRadius, GoalUtils.mobRestricted(mob, horizontalRadius), blockPos);
		});
	}

	/**
	 * 若满足全部约束，则校验并返回朝向给定方向的位置。
	 *
	 * @param entitycreature 寻路生物
	 * @param i              搜索半径
	 * @param flag           该生物是否受限
	 * @param blockposition  目标方向
	 * @return 校验通过的位置；若无效则返回 {@code null}
	 */
	private static BlockPos generateRandomPosTowardDirection(PathfinderMob entitycreature, int i, boolean flag, BlockPos blockposition) {
		BlockPos lePos = RandomPos.generateRandomPosTowardDirection(entitycreature, i, entitycreature.getRandom(), blockposition);
		return !GoalUtils.isOutsideLimits(lePos, entitycreature) && !GoalUtils.isRestricted(flag, entitycreature, lePos) && !GoalUtils.isNotStable(entitycreature.getNavigation(), lePos) ? lePos : null;
	}

	/**
	 * 应用空闲游动时的移动速度。
	 *
	 * <p>在水中时使用比地面宠物更快的速度，以抵消水的阻力：
	 * <ul>
	 *   <li>主人静止：0.25（平缓游动）</li>
	 *   <li>主人移动：0.35（活跃游动）</li>
	 * </ul>
	 *
	 * <p>宠物不在水中时回退到父类的地面速度。
	 */
	@Override
	protected void applySpeed() {
		boolean petInWater = petEntity.isInWater() || petEntity.getInBlockState().is(Blocks.BUBBLE_COLUMN);

		if (petInWater) {
			double walkSpeed = ownerStationary ? SWIM_SPEED_STATIONARY : SWIM_SPEED_MOVING;
			nav.getParameters().addSpeedModifier("RandomStroll", walkSpeed);
		} else {
			super.applySpeed();
		}
	}
}
