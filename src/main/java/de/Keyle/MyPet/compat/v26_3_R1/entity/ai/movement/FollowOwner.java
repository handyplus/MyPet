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

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.entity.ai.AIGoal;
import de.Keyle.MyPet.api.entity.ai.navigation.AbstractNavigation;
import de.Keyle.MyPet.api.util.Compat;
import de.Keyle.MyPet.compat.v26_3_R1.entity.EntityMyFlyingPet;
import de.Keyle.MyPet.compat.v26_3_R1.entity.EntityMyPet;
import de.Keyle.MyPet.compat.v26_3_R1.entity.ai.navigation.MyAquaticPetPathNavigation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * 让宠物跟随其主人的 AI goal，具备智能速度匹配与移动控制。
 *
 * <p>该 goal 实现了能适应不同场景的跟随主人行为：
 * <ul>
 *   <li><b>巡航模式</b>（距离 &lt; 10 格）：速度匹配主人的移动速度，实现自然跟随</li>
 *   <li><b>远距离模式</b>（距离 &gt;= 10 格）：以加速后的速度快速追上</li>
 *   <li><b>传送</b>：当宠物落后太远时自动传送</li>
 * </ul>
 *
 * <p>该 goal 对三类宠物采取不同处理方式：
 * <ul>
 *   <li><b>地面宠物</b>：基于寻路器的移动，直接控制 MOVEMENT_SPEED 属性</li>
 *   <li><b>飞行宠物</b>：基于速度的移动，并在主人上方维持悬停高度</li>
 *   <li><b>游泳宠物</b>：基于速度的移动，并补偿水的阻力</li>
 * </ul>
 *
 * <p>主要特性包括：
 * <ul>
 *   <li>通过位置差值追踪主人速度（而不是不可靠的 getDeltaMovement）</li>
 *   <li>速度转向以避免冲过头</li>
 *   <li>接近主人时渐进式减速</li>
 *   <li>主人处于水中时，非水生宠物在水面游泳</li>
 * </ul>
 *
 * @see MyPetRandomStroll
 * @see Control
 */
@Compat("v26_3_R1")
public class FollowOwner implements AIGoal {

	// ==================== 可调常量 ====================
	// 这些值控制跟随主人的行为，可调整以微调移动表现。
	// 除特别注明外，所有距离值单位为格，速度单位为格/tick。

	// -------------------------------------------------------------------------
	// 距离阈值 - 定义各移动模式之间的分界
	// -------------------------------------------------------------------------

	/**
	 * 巡航模式激活的最大距离（格）。
	 * 该距离以内，宠物速度随主人的移动速度缩放。
	 * 超过该距离则启用远距离模式，以加速后的速度追赶。
	 */
	private static final double CRUISE_RANGE = 10.0;

	/**
	 * "近距离" 速度设置的距离阈值（格）。
	 * 当宠物与主人的距离小于该值时，使用更慢的倍率，
	 * 以避免冲过头并在主人周围来回摆动。
	 */
	private static final double CLOSE_RANGE = 4.0;

	/**
	 * 速度从近距离过渡到追赶段的距离阈值（格）。
	 * 在 CLOSE_RANGE 与 TRANSITION_RANGE 之间，速度倍率线性插值。
	 * 超过 TRANSITION_RANGE（但不超过 CRUISE_RANGE）时，应用完整的追赶倍率。
	 */
	private static final double TRANSITION_RANGE = 5.0;

	/**
	 * 完全停止行为的距离平方阈值。
	 * 当宠物距离小于 sqrt(2.0) ≈ 1.4 格时停止导航
	 * 并将速度归零，以避免在主人周围抖动。
	 */
	private static final double FULL_STOP_DIST_SQ = 2.0;

	/**
	 * 水面游泳停止时的水平距离（格）。
	 * 当非水生宠物在水面朝水中的主人游动时，
	 * 一旦进入该水平距离内就停下并漂浮。
	 */
	private static final double SURFACE_SWIM_CLOSE = 3.0;

	// -------------------------------------------------------------------------
	// 主人移动追踪 - 检测主人速度的参数
	// -------------------------------------------------------------------------

	/**
	 * 视为主人静止的速度阈值（格/tick）。
	 * 正常行走约 0.1 格/tick，疾跑约 0.13，速度 2 行走约 0.2。
	 * 取 0.03 可检测出主人站在原地或几乎不移动的情况。
	 */
	private static final double OWNER_STATIONARY_THRESHOLD = 0.03;

	/**
	 * 指数移动平均（EMA）中当前 tick 速度的权重。
	 * 值越高，速度追踪越灵敏但越容易抖动。
	 * 公式：newSpeed = RESPONSIVE * current + STABLE * previous
	 */
	private static final double SPEED_SMOOTHING_RESPONSIVE = 0.2;

	/**
	 * 指数移动平均（EMA）中上一次速度的权重。
	 * 值越高，速度追踪越稳定但反应越慢。
	 * 为满足 EMA 条件，必须等于 (1.0 - SPEED_SMOOTHING_RESPONSIVE)。
	 */
	private static final double SPEED_SMOOTHING_STABLE = 0.8;

	/**
	 * 主人速度峰值追踪器每 tick 的衰减率。
	 * 峰值追踪可防止主人在短暂停顿时宠物过早减速。
	 * 取 0.95 表示峰值每 tick 衰减约 5%。
	 */
	private static final double SPEED_PEAK_DECAY = 0.95;

	// -------------------------------------------------------------------------
	// 速度倍率上限 - 防止玩家速度很高时出现极端速度
	// -------------------------------------------------------------------------

	/**
	 * 宠物的速度倍率上限。
	 */
	private static final double MAX_SPEED_MULTIPLIER = 2.5;

	// -------------------------------------------------------------------------
	// 飞行/游泳宠物的巡航倍率 - 用于基于速度移动的宠物
	// -------------------------------------------------------------------------

	/**
	 * 飞行/游泳宠物靠近主人（< CLOSE_RANGE）时的速度倍率。
	 * 基于主人的移动速度计算宠物的巡航速度。
	 * 比地面宠物更高，因为速度上限会限制实际移动。
	 */
	private static final double FLY_SWIM_CRUISE_CLOSE_MULT = 1.5;

	/**
	 * 飞行/游泳宠物追赶时（> TRANSITION_RANGE）的速度倍率。
	 * 这是该类宠物在巡航模式下使用的最大倍率。
	 */
	private static final double FLY_SWIM_CRUISE_FAR_MULT = 2.5;

	/**
	 * 飞行/游泳宠物在 TRANSITION_RANGE 之外的倍率距离因子。
	 * 每超出过渡距离 1 格，distanceFactor 就增加该值。
	 * 示例：距离 8 格（超出过渡 3 格）时，factor = 1.0 + 3 * 0.15 = 1.45
	 */
	private static final double FLY_SWIM_CRUISE_DIST_FACTOR = 0.15;

	// -------------------------------------------------------------------------
	// 地面宠物的巡航倍率 - 用于基于寻路的宠物
	// -------------------------------------------------------------------------

	/**
	 * 地面宠物靠近主人（< CLOSE_RANGE）时的速度倍率。
	 * 比飞行/游泳宠物更低，因为地面寻路更高效，
	 * 且没有速度上限的额外开销。
	 */
	private static final double GROUND_CRUISE_CLOSE_MULT = 1.2;

	/**
	 * 地面宠物追赶时（> TRANSITION_RANGE）的速度倍率。
	 * 这是地面宠物在巡航模式下使用的最大倍率。
	 */
	private static final double GROUND_CRUISE_FAR_MULT = 1.8;

	/**
	 * 地面宠物在过渡区间的速度插值系数。
	 * 在 CLOSE_RANGE 与 TRANSITION_RANGE 之间，倍率按以下方式插值：
	 * mult = CLOSE_MULT + (distance - CLOSE_RANGE) * TRANSITION_SCALE
	 */
	private static final double GROUND_CRUISE_TRANSITION_SCALE = 0.6;

	/**
	 * 地面宠物在 TRANSITION_RANGE 之外的倍率距离因子。
	 * 每超出过渡距离 1 格，distanceFactor 就增加该值。
	 * 比飞行/游泳宠物更低，因为地面寻路更直接。
	 */
	private static final double GROUND_CRUISE_DIST_FACTOR = 0.1;

	// -------------------------------------------------------------------------
	// 最低巡航速度 - 防止移动过慢的下限值
	// -------------------------------------------------------------------------

	/**
	 * 飞行与游泳宠物的最低巡航速度。
	 * 确保主人静止或缓慢移动时宠物仍保持移动。
	 * 比地面宠物更高，原因是 3D 寻路开销与水的阻力。
	 */
	private static final float FLY_SWIM_MIN_CRUISE = 0.5f;

	/**
	 * 地面宠物的最低巡航速度。
	 * 确保主人缓慢移动时宠物仍能灵敏响应。
	 * 比飞行/游泳宠物更低，因为地面寻路更高效。
	 */
	private static final float GROUND_MIN_CRUISE = 0.25f;

	// -------------------------------------------------------------------------
	// 飞行宠物专用 - 空中宠物行为的参数
	// -------------------------------------------------------------------------

	/**
	 * 在主人位置上方保持的目标悬停高度（格）。
	 * 飞行宠物维持该高度偏移以保持可见，
	 * 并避免与主人的模型重叠。
	 */
	private static final double HOVER_HEIGHT = 1.5;

	/**
	 * 飞行宠物超出 CLOSE_RANGE 时附加的加速量。
	 * 由于飞行宠物的 3D 寻路更复杂，这有助于它们跟上主人。
	 */
	private static final float FLYING_BONUS_SPEED = 0.1f;

	/**
	 * 飞行宠物在远距离模式下的最大行走速度。
	 * 限制寻路器请求的速度，以防过度加速，
	 * 否则随后会被速度上限猛烈拉回。
	 */
	private static final float FLYING_WALK_SPEED_CAP = 1.0f;

	// -------------------------------------------------------------------------
	// 游泳宠物专用 - 水生宠物行为的参数
	// -------------------------------------------------------------------------

	/**
	 * 为游泳宠物附加的加速量，用于抵消水的阻力。
	 * 水会显著减慢实体移动，因此水生宠物需要
	 * 更高的基础速度才能跟上主人。
	 */
	private static final float SWIMMING_BONUS_SPEED = 0.6f;

	/**
	 * 主人拥有海豚的恩惠效果时的额外加速量。
	 * 使宠物在主人因该效果游得更快时仍能跟上。
	 */
	private static final float DOLPHINS_GRACE_BONUS = 0.08f;

	/**
	 * 水面游泳的最高速度（非水生宠物在水中跟随主人时）。
	 * 限制水平速度，防止出现不切实际的水面滑行。
	 */
	private static final double SURFACE_SWIM_MAX_SPEED = 0.3;

	/**
	 * 水面游泳在进行距离缩放之前的基础速度。
	 * 与距离因子组合：speed = min(MAX, BASE + distance * 0.01)
	 */
	private static final double SURFACE_SWIM_BASE_SPEED = 0.1;

	/**
	 * 水面游泳的速度混合系数。
	 * 控制当前速度与目标速度各占多少。
	 * 取 0.5 表示 50% 当前速度 + 50% 目标速度，以实现平滑过渡。
	 */
	private static final double SURFACE_SWIM_BLEND = 0.5;

	// -------------------------------------------------------------------------
	// 速度上限余量 - 控制相对主人速度的最大速度
	// -------------------------------------------------------------------------

	/**
	 * 宠物靠近主人（< CLOSE_RANGE）时的速度上限余量。
	 * 宠物的最大速度 = ownerSpeed * margin。值 < 1.0 表示宠物
	 * 比主人移动得慢，以防止追赶时的来回摆动。
	 */
	private static final double VEL_CAP_CLOSE_MARGIN = 0.75;

	/**
	 * 飞行/游泳宠物追赶时的速度上限余量。
	 * 值 > 1.0 允许宠物比主人移动得更快，以缩短距离。
	 * 由于 3D 寻路效率较低，该值高于地面宠物。
	 */
	private static final double VEL_CAP_FAR_MARGIN_HIGH = 1.1;

	/**
	 * 地面宠物追赶时的速度上限余量。
	 * 值 < 1.0 可防止地面宠物冲过头，因为其
	 * 寻路比飞行/游泳宠物更直接高效。
	 */
	private static final double VEL_CAP_FAR_MARGIN_LOW = 0.80;

	/**
	 * 飞行/游泳宠物的最低速度上限（格/tick）。
	 * 确保主人静止时宠物仍能移动。
	 * 由于水的阻力开销，该值高于地面宠物。
	 */
	private static final double VEL_CAP_MIN_HIGH = 0.25;

	/**
	 * 地面宠物的最低速度上限（格/tick）。
	 * 确保主人静止时宠物仍能移动。
	 */
	private static final double VEL_CAP_MIN_LOW = 0.15;

	// -------------------------------------------------------------------------
	// 转向 - 朝主人修正方向的参数
	// -------------------------------------------------------------------------

	/**
	 * 转向激活的基础对齐阈值（夹角的余弦值）。
	 * 取 0.5 ≈ cos(60°)，即宠物偏离直线路径超过
	 * 60° 时才会激活转向。
	 */
	private static final double STEER_BASE_THRESHOLD = 0.5;

	/**
	 * 高速时的最大对齐阈值（夹角的余弦值）。
	 * 取 0.7 ≈ cos(45°)。速度越高，转向越早激活，
	 * 以防止因惯性冲过头。
	 */
	private static final double STEER_MAX_THRESHOLD = 0.7;

	/**
	 * 基础转向强度系数。
	 * 决定速度朝主人方向偏转的激进程度。
	 * 值越高 = 转弯越急，值越低 = 曲线越平滑。
	 */
	private static final double STEER_BASE_FACTOR = 0.3;

	/**
	 * 最大转向强度系数。
	 * 限制宠物的转向激进程度，防止出现生硬的
	 * 瞬间变向。在严重偏离航线时应用。
	 */
	private static final double STEER_MAX_FACTOR = 0.85;

	/**
	 * 执行转向所需的最低水平速度（格/tick）。
	 * 低于该速度时跳过转向，避免几乎静止时的抖动。
	 */
	private static final double STEER_MIN_SPEED = 0.1;

	/**
	 * 执行转向所需的最小主人距离（格）。
	 * 低于该距离时跳过转向，因为此时方向不太重要。
	 */
	private static final double STEER_MIN_DIST = 0.5;

	// -------------------------------------------------------------------------
	// 减速 - 接近主人时减速的参数
	// -------------------------------------------------------------------------

	/**
	 * 飞行/游泳宠物的最小减速距离（格）。
	 * 在该距离内激活减速逻辑，以防止冲过头。
	 * 实际减速距离可能更大，取决于速度倍率。
	 */
	private static final double BRAKE_MIN_DISTANCE = 3.0;

	/**
	 * 执行减速前的最低速度（格/tick）。
	 * 如果宠物已经在缓慢移动，则跳过减速。
	 */
	private static final double BRAKE_SPEED_THRESHOLD = 0.25;

	/**
	 * 距离为 0（正好在主人处）时的减速系数。
	 * 速度会乘以该系数，因此 0.3 表示降至 30% 速度。
	 * 值越低 = 距离很近时减速越强。
	 */
	private static final double BRAKE_FACTOR_MIN = 0.3;

	/**
	 * 减速距离边界处的减速系数。
	 * 在减速区间内根据距离从 MIN 插值到 MAX。
	 * 值越高 = 减速区间边缘的减速越平缓。
	 */
	private static final double BRAKE_FACTOR_MAX = 0.7;

	// -------------------------------------------------------------------------
	// 远距离模式 - 超出 CRUISE_RANGE 后追赶的参数
	// -------------------------------------------------------------------------

	/**
	 * 远距离模式下的最大加速量（叠加到基础行走速度上）。
	 * 当宠物远远落后于主人并需要快速追上时应用。
	 */
	private static final float FAR_SPEED_BOOST_MAX = 0.07f;

	/**
	 * 超出 TRANSITION_RANGE 后每格距离的加速缩放系数。
	 * boost = min(MAX, (distance - TRANSITION_RANGE) * SCALE)
	 * 随着宠物落后越多，逐步增大速度。
	 */
	private static final double FAR_SPEED_BOOST_SCALE = 0.014;

	/**
	 * 远距离模式下的最大速度倍率。
	 * 限制实际生效的倍率以避免不切实际的速度，
	 * 即使主人使用了较高的 /speed 设置。
	 */
	private static final float FAR_MAX_MULTIPLIER = 1.5f;

	// -------------------------------------------------------------------------
	// 基础值 - 计算中用到的基本常量
	// -------------------------------------------------------------------------

	/**
	 * 地面宠物计算行走速度时应用的基础倍率。
	 * 飞行宠物改用 1.0，因为它们需要更高的基础速度。
	 */
	private static final float BASE_SPEED_MULTIPLIER = 0.3f;

	/**
	 * 玩家的正常行走速度（格/tick）。
	 * 当玩家通过 /speed 指令或效果修改了行走速度时，
	 * 用作计算速度比值的参考值。
	 */
	private static final float NORMAL_WALK_SPEED = 0.1f;

	/**
	 * 两次看向主人更新之间的间隔（tick）。
	 * 控制宠物转头看向主人的频率。
	 * 值越低 = 头部追踪越灵敏，但 CPU 开销越高。
	 */
	private static final int LOOKAT_INTERVAL = 5;

	/**
	 * 主人静止时的距离平方约束。
	 * 必须与 {@link MyPetRandomStroll#STATIONARY_MAX_DIST_SQ} 一致，以防止出现
	 * FollowOwner 与 RandomStroll 都不激活的死区。
	 * 取 9.0 = 3 格的平方。
	 */
	private static final double STATIONARY_MAX_DIST_SQ = 9.0;

	// ==================== 实例字段 ====================

	private final EntityMyPet petEntity;
	private final AbstractNavigation nav;
	private final float stopDistance;
	private final double startDistance;
	private final float teleportDistance;
	private Control controlPathfinderGoal;
	private final Player owner;
	private boolean waitForGround = false;
	private final boolean flyingPet;

	private int lookAtTimer = 0;
	protected int setPathTimer = 0;
	private boolean hasPathToOwner = false;

	// 追踪主人位置以计算实际移动量（对玩家而言 getDeltaMovement 不可靠）
	private double lastOwnerX = 0;
	private double lastOwnerZ = 0;
	private double ownerMovementSpeed = 0; // Smoothed actual movement per tick
	private double ownerSpeedPeak = 0; // Peak speed with slow decay - prevents premature slowing
	private int lastTrackingTick = -1; // Detect gaps when FollowOwner wasn't active

	/**
	 * 为指定的宠物实体创建新的 FollowOwner AI goal。
	 *
	 * @param entityMyPet       将跟随其主人的宠物实体
	 * @param startDistance     宠物开始跟随的最小距离（内部会平方处理）
	 * @param stopDistance      宠物停止跟随的距离（内部会平方处理）
	 * @param teleportDistance  宠物传送至主人的距离（内部会平方处理）
	 */
	public FollowOwner(EntityMyPet entityMyPet, double startDistance, float stopDistance, float teleportDistance) {
		this.petEntity = entityMyPet;
		this.nav = entityMyPet.getPetNavigation();
		this.startDistance = startDistance * startDistance;
		this.stopDistance = stopDistance * stopDistance;
		this.teleportDistance = teleportDistance * teleportDistance;
		this.owner = ((CraftPlayer) petEntity.getOwner().getPlayer()).getHandle();
        this.flyingPet = entityMyPet instanceof EntityMyFlyingPet;
    }

    /**
     * 检查宠物当前是否在游泳（在水中且使用水生导航）。
     *
     * <p>与 {@link #flyingPet} 不同，这里进行动态判断，因为水生宠物
     * 可以在水中与陆地之间切换。
     *
     * @return 宠物在水中且使用水生导航时返回 {@code true}
     */
    private boolean isSwimmingPet() {
        return (this.petEntity.isInWater() || this.petEntity.getInBlockState().is(Blocks.BUBBLE_COLUMN))
                && this.petEntity.getNavigation() instanceof MyAquaticPetPathNavigation;
    }

    /**
     * 检查宠物是否具备水生导航能力。
     *
     * @return 宠物可在水下寻路时返回 {@code true}
     */
    private boolean canPetSwim() {
        return this.petEntity.getNavigation() instanceof MyAquaticPetPathNavigation;
    }

    /**
     * 检查主人是否在水中而宠物无法游泳。
     *
     * <p>当该条件成立时，宠物将使用水面游泳行为
     * 在水平方向上跟随主人，而不是尝试水下寻路。
     *
     * @return 主人在水中且宠物不具备水生导航时返回 {@code true}
     */
    private boolean isOwnerInWaterAndPetCantSwim() {
        return owner.isInWater() && !canPetSwim() && !flyingPet;
    }

    // 某些 Paper 构建把 Abilities#getWalkingSpeed 的重映射为比编译期 API 声明的更窄的可访问级别，
    // 因此该调用会在运行时抛出 IllegalAccessError。回退到
    // 原版默认速度，而不是让 AI goal 崩溃。
    private float getWalkingSpeed(Player player) {
        try {
            return player.getAbilities().getWalkingSpeed();
        } catch (LinkageError e) {
            return NORMAL_WALK_SPEED;
        }
    }

    /** 追踪宠物当前是否正在朝主人进行水面游泳。 */
    private boolean surfaceSwimmingToOwner = false;

	@Override
	public boolean shouldStart() {
		if (controlPathfinderGoal == null) {
			if (petEntity.getPathfinder().hasGoal("Control")) {
				controlPathfinderGoal = (Control) petEntity.getPathfinder().getGoal("Control");
			}
		}
		if (!this.petEntity.canMove()) {
			return false;
		} else if (this.petEntity.getMyPetTarget() != null && !this.petEntity.getMyPetTarget().isDead()) {
			return false;
		} else if (this.petEntity.getOwner() == null) {
            return false;
        }

        // 主人静止时使用更严格的约束，以消除与 RandomStroll 之间的死区
        // ownerMovementSpeed 在第一个 tick 可能还未初始化，因此也检查位置差值
        double distSqToOwner = this.petEntity.distanceToSqr(owner);
        double effectiveStartDistance = this.startDistance;
        if (ownerMovementSpeed < OWNER_STATIONARY_THRESHOLD) {
            // 主人静止 - 使用与 RandomStroll 相同的严格约束
            effectiveStartDistance = STATIONARY_MAX_DIST_SQ;
        }

        if (distSqToOwner < effectiveStartDistance) {
            return false;
        }
        return controlPathfinderGoal == null || controlPathfinderGoal.moveTo == null;
	}

	@Override
	public boolean shouldFinish() {
		if (controlPathfinderGoal != null && controlPathfinderGoal.moveTo != null) {
			return true;
		} else if (this.petEntity.getOwner() == null) {
            return true;
        }

        // 主人静止时使用更紧的停止距离
        double effectiveStopDistance = this.stopDistance;
        if (ownerMovementSpeed < OWNER_STATIONARY_THRESHOLD) {
            effectiveStopDistance = STATIONARY_MAX_DIST_SQ;
        }

        if (this.petEntity.distanceToSqr(owner) < effectiveStopDistance) {
			return true;
		} else if (!this.petEntity.canMove()) {
			return true;
		} else return this.petEntity.getMyPetTarget() != null && !this.petEntity.getMyPetTarget().isDead();
	}

	@Override
    public void start() {
        double distance = Math.sqrt(this.petEntity.distanceToSqr(owner));
		applyWalkSpeed(distance);
		this.setPathTimer = 0;
	}

	@Override
	public void finish() {
		nav.getParameters().removeSpeedModifier("FollowOwner");
		this.nav.stop();
		this.setPathTimer = 0;
	}

	@Override
    public void tick() {
        // 使用 NMS 层级的检测以避免 Location 对象分配
        if (this.petEntity.level() != owner.level()) {
            return;
        }

        // 通过追踪位置变化计算主人的实际移动量
        // 必须在 tick() 早期完成，以便速度逻辑能用上 ownerMovementSpeed
        double currentOwnerX = owner.getX();
        double currentOwnerZ = owner.getZ();
        int currentTick = petEntity.tickCount;
        int ticksSinceLastUpdate = currentTick - lastTrackingTick;
        lastTrackingTick = currentTick;

        // 只有在追踪连续（无间隔）时才计算移动量
        // 当 FollowOwner 未激活（例如处于 RandomStroll 期间）时会出现间隔
        if (ticksSinceLastUpdate <= 2 && ticksSinceLastUpdate > 0) {
            double dx = currentOwnerX - lastOwnerX;
            double dz = currentOwnerZ - lastOwnerZ;
            double currentMovement = Math.sqrt(dx * dx + dz * dz);
            // 如果跨过了多个 tick，则归一化为每 tick 的值
            if (ticksSinceLastUpdate > 1) {
                currentMovement /= ticksSinceLastUpdate;
            }
            // 使用较强的平滑以获得稳定性（灵敏 vs 平滑）
            ownerMovementSpeed = SPEED_SMOOTHING_RESPONSIVE * currentMovement + SPEED_SMOOTHING_STABLE * ownerMovementSpeed;
            // 峰值追踪：缓慢衰减，但如果当前值超过峰值则直接上跳
            ownerSpeedPeak = ownerSpeedPeak * SPEED_PEAK_DECAY;
            if (ownerMovementSpeed > ownerSpeedPeak) {
                ownerSpeedPeak = ownerMovementSpeed;
            }
        }
        // 始终更新所追踪的位置（即使有间隔，也为下一个 tick 做准备）
        lastOwnerX = currentOwnerX;
        lastOwnerZ = currentOwnerZ;

        double distanceSqr = this.petEntity.distanceToSqr(owner);
        double distance = Math.sqrt(distanceSqr);

		// 每 LOOKAT_INTERVAL 个 tick 才看向主人
		if (--lookAtTimer <= 0) {
			this.petEntity.getLookControl().setLookAt(owner, this.petEntity.getMaxHeadXRot(), this.petEntity.getMaxHeadXRot());
			lookAtTimer = LOOKAT_INTERVAL;
        }

        // 检查主人是否在水中（游泳/浮沉）且宠物无法游泳
        // 不会游泳的宠物（僵尸等）应在水面朝主人的
        // 水平位置游动，而不是尝试下潜或频繁传送
        if (isOwnerInWaterAndPetCantSwim()) {
            if (!surfaceSwimmingToOwner) {
                surfaceSwimmingToOwner = true;
            }

            // 计算与主人的水平距离（忽略 Y）
            double dx = owner.getX() - petEntity.getX();
            double dz = owner.getZ() - petEntity.getZ();
            double horizontalDistSq = dx * dx + dz * dz;
            double horizontalDist = Math.sqrt(horizontalDistSq);

            // 如果水平距离已经足够近，就原地漂浮
            if (horizontalDist < SURFACE_SWIM_CLOSE) {
                return;
            }

            // 朝主人的水平位置在水面游泳
            // 使用基于速度的移动，因为水下无法使用普通寻路
            Vec3 vel = petEntity.getDeltaMovement();

            // 归一化水平方向
            double dirX = dx / horizontalDist;
            double dirZ = dz / horizontalDist;

            // 水面游泳速度 - 随距离缩放但设上限
            double swimSpeed = Math.min(SURFACE_SWIM_MAX_SPEED, SURFACE_SWIM_BASE_SPEED + horizontalDist * 0.01);

            // 朝主人施加水平速度，保留现有的 Y 速度（浮力）
            double newVelX = dirX * swimSpeed;
            double newVelZ = dirZ * swimSpeed;

            // 与现有速度混合，使移动更平滑
            newVelX = vel.x * SURFACE_SWIM_BLEND + newVelX * SURFACE_SWIM_BLEND;
            newVelZ = vel.z * SURFACE_SWIM_BLEND + newVelZ * SURFACE_SWIM_BLEND;

            Vec3 newVel = new Vec3(newVelX, vel.y, newVelZ);
            petEntity.setDeltaMovement(newVel);

            // 游泳时看向主人
            this.petEntity.getLookControl().setLookAt(owner, this.petEntity.getMaxHeadXRot(), this.petEntity.getMaxHeadXRot());

            // 不要进行普通寻路或传送 - 移动由我们自行处理
            return;
        } else if (surfaceSwimmingToOwner) {
            surfaceSwimmingToOwner = false;
		}

		// 传送
		if (this.petEntity.canMove()) {
			if ((!owner.getAbilities().flying && !owner.getBukkitEntity().isGliding()) || flyingPet) {
				if (!waitForGround) {
					if (owner.fallDistance <= 4 || flyingPet) {
                        if (distanceSqr >= this.teleportDistance) {
                            if (controlPathfinderGoal.moveTo == null && !petEntity.hasTarget()) {
                                Location ownerLocation = owner.getBukkitEntity().getLocation();
                                if (MyPetApi.getPlatformHelper().canSpawn(ownerLocation, this.petEntity)) {
                                    // 传送前停止导航并清零速度
                                    this.nav.stop();
                                    this.petEntity.setDeltaMovement(Vec3.ZERO);
                                    // 不进行碰撞检测的传送
                                    this.petEntity.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                                    this.petEntity.fallDistance = 0;
                                    this.setPathTimer = 0;
									return;
								}
							}
						}
					}
				} else if (owner.onGround()) {
					waitForGround = false;
				}
			} else {
                waitForGround = true;
            }

            // 离开完全停止距离后再跟随主人
            if (distanceSqr > FULL_STOP_DIST_SQ) {
                // 对所有宠物施加速度控制，以防冲过头
                // 地面宠物此前过快，因为行走速度并不限制实际速度
                boolean swimmingPet = isSwimmingPet();
                {
                    Vec3 vel = this.petEntity.getDeltaMovement();
                    float ownerSpeed = getWalkingSpeed(owner);
                    // 限制速度倍率 - 宠物无法承受极端速度
                    // 在速度 5（0.5 行走速度）时，原始倍率会是 5.0，按 MAX_SPEED_MULTIPLIER 限制
                    double rawSpeedMultiplier = Math.max(1.0, ownerSpeed / NORMAL_WALK_SPEED);
                    double speedMultiplier = Math.min(MAX_SPEED_MULTIPLIER, rawSpeedMultiplier);

                    // Y 轴控制：飞行宠物应在主人上方悬停
                    // 地面/游泳宠物不需要这样
                    if (flyingPet) {
                        double targetYLevel = owner.getY() + HOVER_HEIGHT;

                        // 相对于目标悬停位置（而不是主人的 Y）计算 Y 偏移
                        double yDiff = targetYLevel - petEntity.getY(); // positive = need to go up, negative = need to go down
                        double maxYVel = 0.5 * Math.min(speedMultiplier, 2.0); // Cap Y at 0.5-1.0

                        // 方向性 Y 轴控制：强制 Y 速度朝向目标悬停高度
                        // 如果蝙蝠高于目标（yDiff < 0），Y 速度应为负（向下）
                        // 如果蝙蝠低于目标（yDiff > 0），Y 速度应为正（向上）
                        double targetY;
                        if (Math.abs(yDiff) < 2.0) {
                            // 接近目标高度 - 只做轻微修正
                            targetY = yDiff * 0.3; // Smooth approach
                        } else if (yDiff < 0) {
                            // 蝙蝠高于目标 - 必须向下
                            // 限定为负值，偏离越高修正越强
                            targetY = Math.max(-maxYVel, yDiff * 0.15);
                            if (vel.y > 0) {
                                // 方向错误！完全覆盖
                                targetY = -maxYVel * 0.8;
                            }
                        } else {
                            // 蝙蝠低于目标 - 向上，但有上限
                            targetY = Math.min(maxYVel, yDiff * 0.15);
                        }

                        // 应用 Y 轴修正：将当前 Y 与目标混合
                        double newY = vel.y * 0.3 + targetY * 0.7;
                        newY = Math.max(-maxYVel, Math.min(maxYVel, newY)); // Hard cap both directions
                        vel = new Vec3(vel.x, newY, vel.z);
                        this.petEntity.setDeltaMovement(vel);
                    } // End of flyingPet Y-control block

                    double speed = vel.length();

                    // 速度上限计算 - 地面宠物与飞行/游泳宠物的余量不同
                    // 飞行/游泳宠物在 3D 中存在寻路效率损失，因此需要更高的余量
                    // 地面宠物的路径更直接，因此余量应更接近 1.0
                    boolean needsHighMargin = flyingPet || swimmingPet;

                    double maxVel;
                    if (distance < CRUISE_RANGE) {
                        // 巡航模式：按主人速度成比例限制速度
                        // 这样可以防止过度加速和来回摆动
                        double distFactor;
                        double margin;
                        if (distance < CLOSE_RANGE) {
                            // 靠近：比主人移动得慢，以防追赶
                            distFactor = 1.0;
                            margin = VEL_CAP_CLOSE_MARGIN;
                        } else if (distance < TRANSITION_RANGE) {
                            // 过渡：渐变
                            distFactor = 1.0;
                            if (needsHighMargin) {
                                margin = VEL_CAP_CLOSE_MARGIN + (distance - CLOSE_RANGE) * 0.35; // -> VEL_CAP_FAR_MARGIN_HIGH
                            } else {
                                margin = VEL_CAP_CLOSE_MARGIN + (distance - CLOSE_RANGE) * 0.05; // -> VEL_CAP_FAR_MARGIN_LOW
                            }
                        } else {
                            // 追赶：仅对飞行/游泳宠物允许更快的移动
                            if (needsHighMargin) {
                                distFactor = 1.0 + (distance - TRANSITION_RANGE) * 0.06; // 1.0 -> 1.3
                                margin = VEL_CAP_FAR_MARGIN_HIGH;
                            } else {
                                // 地面宠物：无加成，余量更紧（寻路器效率高）
                                distFactor = 1.0;
                                margin = VEL_CAP_FAR_MARGIN_LOW;
                            }
                        }
                        maxVel = ownerMovementSpeed * distFactor * margin;
                        // 速度下限，确保主人缓慢/静止时宠物仍能移动
                        maxVel = Math.max(maxVel, needsHighMargin ? VEL_CAP_MIN_HIGH : VEL_CAP_MIN_LOW);
                    } else {
                        // 远距离模式：使用基于距离的公式实现快速追赶
                        double ownerBasedMinVel = ownerSpeedPeak * (needsHighMargin ? 1.3 : 1.1);
                        maxVel = Math.max(speedMultiplier, ownerBasedMinVel);
                    }

                    // 计算朝主人的方向（用于转向的水平向量）
                    Vec3 toOwner = new Vec3(
                            owner.getX() - petEntity.getX(),
                            0,
                            owner.getZ() - petEntity.getZ()
                    );
                    Vec3 horizontalVel = new Vec3(vel.x, 0, vel.z);
                    double horizontalSpeed = horizontalVel.length();
                    double toOwnerDist = toOwner.length();

                    // 计算对齐度：dot / (|vel| * |toOwner|) = cos(夹角)
                    // 1.0 = 正朝目标移动，0.0 = 垂直，-1.0 = 正在远离
                    double alignment = 0;
                    if (horizontalSpeed > 0.05 && toOwnerDist > 0.1) {
                        alignment = horizontalVel.dot(toOwner) / (horizontalSpeed * toOwnerDist);
                    }

                    // 转向：根据对齐度持续朝主人修正方向
                    // 速度越高越早开始转向（阈值更高），以防冲过头
                    // 基础阈值：STEER_BASE_THRESHOLD（60°），高速时：STEER_MAX_THRESHOLD（45°）
                    double steerThreshold = STEER_BASE_THRESHOLD + (speedMultiplier - 1.0) * 0.13;
                    steerThreshold = Math.min(STEER_MAX_THRESHOLD, steerThreshold);

                    if (horizontalSpeed > STEER_MIN_SPEED && toOwnerDist > STEER_MIN_DIST) {
                        Vec3 toOwnerNormalized = toOwner.normalize();

                        // 转向强度：偏离越大、距离越近时越强
                        if (alignment < steerThreshold) {
                            // 计算转向幅度：将当前速度朝目标方向混合
                            // 高速时转向更激进
                            double baseSteer = STEER_BASE_FACTOR + (speedMultiplier - 1.0) * 0.1;
                            double steerFactor = baseSteer + (steerThreshold - alignment) * 0.4;
                            steerFactor = Math.min(STEER_MAX_FACTOR, steerFactor);

                            // 将当前速度与目标方向混合
                            double keepFactor = 1.0 - steerFactor;
                            double newVelX = horizontalVel.x * keepFactor + toOwnerNormalized.x * horizontalSpeed * steerFactor;
                            double newVelZ = horizontalVel.z * keepFactor + toOwnerNormalized.z * horizontalSpeed * steerFactor;

                            Vec3 newVel = new Vec3(newVelX, vel.y, newVelZ);
                            this.petEntity.setDeltaMovement(newVel);
                            // 更新 vel 供后续检查使用
                            vel = newVel;
                            speed = vel.length();
                            horizontalVel = new Vec3(vel.x, 0, vel.z);
                            horizontalSpeed = horizontalVel.length();
                        }
                    }

                    // 减速与速度上限：仅针对飞行/游泳宠物（基于速度的移动）
                    // 地面宠物使用基于位置的寻路，并直接控制 MOVEMENT_SPEED 属性
                    if (flyingPet || swimmingPet) {
                        // 减速：靠近且速度较快时减速，以防冲过头
                        // 越接近就越多地压低目标速度 - 很近时降到主人速度以下
                        double brakeDistance = Math.max(BRAKE_MIN_DISTANCE, 5.0 / speedMultiplier);
                        // minBrakeSpeed 的缩放：在 brakeDistance 处为 ownerSpeed，dist=0 处为 BRAKE_FACTOR_MAX*ownerSpeed
                        double distRatio = Math.min(1.0, distance / brakeDistance);
                        double minBrakeSpeed = ownerMovementSpeed * (BRAKE_FACTOR_MAX + (1.0 - BRAKE_FACTOR_MAX) * distRatio);
                        // 关键：减速的下限不能超过速度上限，否则减速会覆盖
                        // 近距离的减速效果，宠物仍会追上
                        minBrakeSpeed = Math.min(minBrakeSpeed, maxVel);
                        if (distance < brakeDistance && speed > BRAKE_SPEED_THRESHOLD && speed > minBrakeSpeed) {
                            // 渐进式减速：越近越强
                            double brakeFactor = BRAKE_FACTOR_MIN + distRatio * (BRAKE_FACTOR_MAX - BRAKE_FACTOR_MIN);
                            double targetSpeed = speed * brakeFactor;
                            // 应用下限，但它现在会随距离缩放
                            if (targetSpeed < minBrakeSpeed) {
                                brakeFactor = minBrakeSpeed / speed;
                                targetSpeed = minBrakeSpeed;
                            }
                            Vec3 newVel = vel.scale(brakeFactor);
                            this.petEntity.setDeltaMovement(newVel);
                        } else if (speed > maxVel) {
                            // 限制速度，防止失控加速
                            Vec3 newVel = vel.normalize().scale(maxVel);
                            this.petEntity.setDeltaMovement(newVel);
                        }
                    }
                }
                // 每 10 个 tick 才重新寻路，每 tick 重算路径开销过大
                if (--this.setPathTimer <= 0) {
                    this.setPathTimer = 10;
                    this.hasPathToOwner = this.nav.navigateTo(owner.getBukkitEntity());
                }
                if (this.hasPathToOwner) {
                    applyWalkSpeed(distance);
                }
            } else {
                // 已经很近 - 停止导航并清理动量（所有宠物）
                this.nav.stop();
                this.setPathTimer = 0;
                this.petEntity.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**
     * 根据与主人的距离计算并应用合适的行走速度。
     *
     * <p>该方法实现了基于距离的速度分区：
     * <ul>
     *   <li><b>巡航模式</b>（{@code distance < CRUISE_RANGE}）：速度与主人的移动成比例</li>
     *   <li><b>远距离模式</b>（{@code distance >= CRUISE_RANGE}）：以加速后的速度追赶</li>
     * </ul>
     *
     * <p>速度修正的应用方式因宠物类型而异：
     * <ul>
     *   <li>飞行/游泳宠物：因速度上限开销而使用更高的倍率</li>
     *   <li>地面宠物：使用更低的倍率并直接控制 MOVEMENT_SPEED 属性</li>
     * </ul>
     *
     * @param distance 宠物与主人之间的当前距离（格）
     */
    private void applyWalkSpeed(double distance) {
        float baseWalkSpeed = getWalkingSpeed(owner);
        float walkSpeed = baseWalkSpeed;

        // 靠近主人时使用巡航速度以匹配其节奏
        // 扩大范围以防在边界处来回摆动
        if (distance < CRUISE_RANGE) {
            // 飞行/游泳宠物有速度上限，因此需要更高的行走速度
            // 地面宠物没有速度上限，因此需要低得多的倍率
            boolean hasVelocityCap = flyingPet || isSwimmingPet();

            double distanceFactor;
            double speedMultiplier;

            if (hasVelocityCap) {
                // 飞行/游泳宠物：更高的倍率以获得更灵敏的移动
                if (distance < CLOSE_RANGE) {
                    distanceFactor = 1.0;
                    speedMultiplier = FLY_SWIM_CRUISE_CLOSE_MULT;
                } else if (distance < TRANSITION_RANGE) {
                    distanceFactor = 1.0;
                    speedMultiplier = FLY_SWIM_CRUISE_CLOSE_MULT + (distance - CLOSE_RANGE); // -> FAR_MULT
                } else {
                    distanceFactor = 1.0 + (distance - TRANSITION_RANGE) * FLY_SWIM_CRUISE_DIST_FACTOR;
                    speedMultiplier = FLY_SWIM_CRUISE_FAR_MULT;
                }
            } else {
                // 地面宠物：直接控制 MOVEMENT_SPEED 属性（在下方设置）
                // 使用基于距离的缩放，以便距离较远时追上
                if (distance < CLOSE_RANGE) {
                    distanceFactor = 1.0;
                    speedMultiplier = GROUND_CRUISE_CLOSE_MULT;
                } else if (distance < TRANSITION_RANGE) {
                    distanceFactor = 1.0;
                    speedMultiplier = GROUND_CRUISE_CLOSE_MULT + (distance - CLOSE_RANGE) * GROUND_CRUISE_TRANSITION_SCALE;
                } else {
                    distanceFactor = 1.0 + (distance - TRANSITION_RANGE) * GROUND_CRUISE_DIST_FACTOR;
                    speedMultiplier = GROUND_CRUISE_FAR_MULT;
                }
            }

            // 地面宠物使用 ownerSpeedPeak（响应快）而不是平滑后的 ownerMovementSpeed
            // 这样可以防止主人开始行走时宠物落后
            double speedForCruise = (flyingPet || isSwimmingPet()) ? ownerMovementSpeed : ownerSpeedPeak;
            float cruiseSpeed = (float) (speedForCruise * speedMultiplier * distanceFactor);

            if (flyingPet && distance >= CLOSE_RANGE) {
                // 仅在非近距离时给予飞行加成
                cruiseSpeed += FLYING_BONUS_SPEED;
            }
            // 游泳宠物在水下需要明显更高的速度以抵消水的阻力
            if (isSwimmingPet()) {
                cruiseSpeed += SWIMMING_BONUS_SPEED;
                if (owner.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                    cruiseSpeed += DOLPHINS_GRACE_BONUS;
                }
            }

            // 飞行/游泳宠物因 3D 寻路开销而需要更高的下限
            // 地面宠物需要合理的下限，以免移动过于迟钝
            float minCruise = (flyingPet || isSwimmingPet()) ? FLY_SWIM_MIN_CRUISE : GROUND_MIN_CRUISE;
            cruiseSpeed = Math.max(minCruise, cruiseSpeed); // Minimum to keep moving
            nav.getParameters().addSpeedModifier("FollowOwner", cruiseSpeed);

            // 地面宠物：每 tick 直接设置实体的 MOVEMENT_SPEED 属性
            // 导航参数变更只在开始新路径时生效，进行中的导航不会应用
            if (!flyingPet && !isSwimmingPet()) {
                petEntity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(cruiseSpeed);
            }

            return;
        }

        // 远距离模式：距离 >= 10 格，需要快速追上
		if (owner.getAbilities().flying) {
			// 玩家飞行时让宠物更快
			walkSpeed += owner.getAbilities().getFlyingSpeed();
		} else if (owner.isSprinting() || owner.getBukkitEntity().isGliding()) {
			// 玩家疾跑时让宠物更快
            AttributeInstance speedAttribute = owner.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
            if (speedAttribute != null) {
                walkSpeed += (float) speedAttribute.getValue() - 0.037f;
            }
        } else if (owner.isPassenger() && owner.getVehicle() instanceof LivingEntity livingVehicle) {
			// 调整速度，使宠物能追上玩家所乘坐的载具
            AttributeInstance vehicleSpeedAttribute = livingVehicle.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
			if (vehicleSpeedAttribute != null) {
				walkSpeed = (float) vehicleSpeedAttribute.getValue();
			}
		}

		// 让水生/飞行宠物更快
		// 这实际上是因为 MovementSpeed 与 FlyingSpeed 之间存在差异，FlyingSpeed 更高
		// （我并不完全清楚游泳为何也是如此，但我想原因类似）
		if((this.petEntity.isInWater() || this.petEntity.getInBlockState().is(Blocks.BUBBLE_COLUMN)) && this.petEntity.getNavigation() instanceof MyAquaticPetPathNavigation) {
			walkSpeed += 0.6f;
			if(owner.hasEffect(MobEffects.DOLPHINS_GRACE)) {
				walkSpeed += 0.08f;
			}
		}

		if(this.petEntity.getMoveControl() instanceof MyPetFlyingMoveControl) {
			walkSpeed += 0.575f;
			if(owner.isSprinting()) {
                walkSpeed -= 0.05f;
            }
        }

        // 基于距离的动态加速：
        // - 近（< TRANSITION_RANGE 格）：完全匹配玩家速度
        // - 远（> CRUISE_RANGE 格）：全力加速以追上
        // - 中间：逐渐增加
        float speedBoost = 0f;
        if (distance > TRANSITION_RANGE) {
            // 随距离增加从 0 缩放到 FAR_SPEED_BOOST_MAX
            speedBoost = (float) Math.min(FAR_SPEED_BOOST_MAX, (distance - TRANSITION_RANGE) * FAR_SPEED_BOOST_SCALE);
        }
        walkSpeed += speedBoost;

        // 动态倍率：玩家移动速度高于正常行走速度时向上缩放
        // 计算比值时只使用 baseWalkSpeed（不含飞行加成），以避免指数级增长
        float speedRatio = Math.max(1.0f, baseWalkSpeed / NORMAL_WALK_SPEED);
        // 飞行宠物需要更高的基础倍率，因为它们的加成本来就被削减太多
        float baseMultiplier = flyingPet ? 1.0f : BASE_SPEED_MULTIPLIER;
        float effectiveMultiplier = baseMultiplier * speedRatio;
        // 限制倍率，防止速度失控
        effectiveMultiplier = Math.min(FAR_MAX_MULTIPLIER, effectiveMultiplier);

        // 对于玩家速度极高时的飞行宠物，限制行走速度，以防
        // 寻路器过度加速（随后又被速度上限猛烈拉回）
        // 让寻路器与速度上限协同工作，从而实现更平滑的运动
        if (flyingPet && walkSpeed > FLYING_WALK_SPEED_CAP) {
            walkSpeed = FLYING_WALK_SPEED_CAP;
        }

        // 应用动态倍率
        float finalSpeed = walkSpeed * effectiveMultiplier;

        nav.getParameters().addSpeedModifier("FollowOwner", finalSpeed);
	}
}
