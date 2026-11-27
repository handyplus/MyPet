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
import de.Keyle.MyPet.compat.v26_3_R1.entity.EntityMyPet;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * AI goal：让地面宠物在静止的主人附近随机漫步。
 *
 * <p>该 goal 仅在主人静止（未移动）且宠物处于近距离范围内时激活。它营造自然的空闲行为，
 * 让宠物在主人周围徘徊，而不是站着不动。
 *
 * <p>主要行为：
 * <ul>
 *   <li>仅在主人移动速度低于阈值（0.03 格/tick）时激活</li>
 *   <li>主人静止时，将漫步范围限制在主人 3 格之内</li>
 *   <li>使用缓慢、放松的移动速度，与玩家的行走速度无关</li>
 *   <li>最多尝试 5 个随机位置以寻找有效的漫步目标</li>
 * </ul>
 *
 * <p>它是 {@link MyPetRandomFly} 和 {@link MyPetRandomSwim} 的基类，
 * 后者为飞行和水生宠物提供专门的漫步行为。
 *
 * @see MyPetRandomFly
 * @see MyPetRandomSwim
 * @see FollowOwner
 */
@Compat("v26_3_R1")
public class MyPetRandomStroll implements AIGoal, de.Keyle.MyPet.api.entity.ai.movement.MyPetRandomStroll {

    // ==================== 可调常量 ====================
    // 这些数值控制随机漫步行为，可以调整以微调空闲漫步。
    // 除非另行说明，所有距离值单位为格，速度单位为格/tick。

    // -------------------------------------------------------------------------
    // 距离阈值 - 定义漫步激活的边界
    // -------------------------------------------------------------------------

    /**
     * 激活漫步时，距主人的最大距离平方（格²）。
     * 宠物必须位于静止主人 sqrt(9) = 3 格范围内才能开始漫步。
     * 必须与 {@link FollowOwner#STATIONARY_MAX_DIST_SQ} 保持一致，以避免出现死区。
     */
    protected static final double STATIONARY_MAX_DIST_SQ = 9.0;

    // -------------------------------------------------------------------------
    // 漫步激活 - 控制漫步何时开始的参数
    // -------------------------------------------------------------------------

    /**
     * 条件满足时，每 tick 开始一次漫步的基础概率（0.0-1.0）。
     * 取 0.02 表示每 tick 有 2% 概率，大约每 2.5 秒尝试一次漫步。
     * 子类会覆盖该值：飞行宠物使用 8%，游泳宠物使用 6%。
     */
    protected static final float DEFAULT_STROLL_CHANCE = 0.02F;

    // -------------------------------------------------------------------------
    // 主人移动检测 - 静止状态检测的参数
    // -------------------------------------------------------------------------

    /**
     * 判定主人静止的速度阈值（格/tick）。
     * 正常行走约为 0.1 格/tick。取 0.03 可检测出主人是否完全站定
     * 或几乎未移动（例如轻微的位置调整）。
     */
    private static final double OWNER_STATIONARY_THRESHOLD = 0.03;

    /**
     * 指数移动平均（EMA）中当前帧速度的权重。
     * 公式：newSpeed = RESPONSIVE * current + (1 - RESPONSIVE) * previous
     * 取值越大 = 反应越快，但检测越容易抖动。
     */
    private static final double SPEED_SMOOTHING_RESPONSIVE = 0.2;

    /**
     * 两次位置更新之间允许的最大 tick 数，超出则视为移动计算无效。
     * 若两次更新之间超过 2 tick（例如 goal 曾处于未激活状态），
     * 则跳过移动计算，以避免得到错误的速度读数。
     */
    private static final int MAX_TRACKING_GAP = 2;

    // -------------------------------------------------------------------------
    // 漫步位置生成 - 目标选择的参数
    // -------------------------------------------------------------------------

    /**
     * 主人静止时的水平漫步半径（格）。
     * 宠物会在该半径内寻找随机位置。
     * 取值较小可使宠物保持在静止主人附近。
     */
    protected static final int WANDER_RADIUS_STATIONARY = 2;

    /**
     * 主人移动时的水平漫步半径（格）。
     * 较大半径可提供更多自由，但很少用到，因为
     * 漫步通常只在主人静止时激活。
     */
    protected static final int WANDER_RADIUS_MOVING = 3;

    /**
     * 静止时，漫步目标距主人的最大距离平方（格²）。
     * 距主人超出该距离的目标会被拒绝。
     */
    protected static final double WANDER_MAX_DIST_SQ_STATIONARY = STATIONARY_MAX_DIST_SQ;

    /**
     * 静止时，漫步目标距宠物当前位置的最大距离平方（格²）。
     * 值 4.0 = 2 格。防止宠物在一次漫步中走得太远。
     */
    protected static final double WANDER_MAX_DIST_SQ_FROM_PET_STATIONARY = 4.0;

    /**
     * 主人移动时，漫步目标距宠物当前位置的最大距离平方（格²）。
     * 值 9.0 = 3 格。允许稍长一些的漫步。
     */
    protected static final double WANDER_MAX_DIST_SQ_FROM_PET_MOVING = 9.0;

    /**
     * 放弃前寻找有效漫步位置的最大尝试次数。
     * 提高该值可增加找到有效位置的概率，但会消耗更多 CPU。
     */
    protected static final int WANDER_MAX_ATTEMPTS = 5;

    // -------------------------------------------------------------------------
    // 移动速度 - 漫步移动的参数
    // -------------------------------------------------------------------------

    /**
     * 地面宠物漫步时的移动速度（格/tick）。
     * 缓慢、放松的步速，与主人配置的行走速度无关。
     * 营造自然的空闲漫步，而非突然猛冲。
     */
    protected static final double STROLL_SPEED = 0.15;

    /**
     * 一次漫步完成所需的最短时间（tick）。
     * 防止出现过短而不自然的漫步。
     */
    protected static final int MIN_STROLL_TIME = 3;

    /**
     * 计算漫步超时所用的距离除数。
     * timeout = max(MIN_STROLL_TIME, distance / TIMEOUT_DIVISOR)
     * 取 3 时，大约每 3 格距离对应 1 tick。
     */
    protected static final int STROLL_TIMEOUT_DIVISOR = 3;

    /**
     * 判定已到达漫步目的地的距离阈值（格）。
     * 当宠物与目标的距离小于该值时，漫步视为成功完成。
     */
    protected static final double DESTINATION_REACHED_THRESHOLD = 0.75;

    // ==================== 实例字段 ====================

    /** 执行漫步行为的宠物实体。 */
    protected final EntityMyPet petEntity;

    /** 用于寻路的导航系统。 */
    protected AbstractNavigation nav;

    /** 当前漫步的目的地；若无进行中的漫步则为 null。 */
    protected Location moveTo = null;

    /** 当前漫步超时前剩余的 tick 数。 */
    protected int timeToMove = 0;

    /** 允许漫步时距主人的最大距离平方。 */
    protected final int startDistance;

    /** 指向 Control goal 的引用，用于检查宠物是否处于手动控制中。 */
    protected Control controlPathfinderGoal;

    /** 宠物的主人，以 NMS Player 实体形式表示。 */
    protected final Player owner;

    /** 每 tick 开始一次漫步的概率（0.0-1.0）。 */
    protected float strollChance = DEFAULT_STROLL_CHANCE;

    /** 主人当前是否静止（移动量小于阈值）。 */
    protected boolean ownerStationary = false;

    /** 用于移动计算的主人上一次记录的 X 坐标。 */
    private double lastOwnerX = 0;

    /** 用于移动计算的主人上一次记录的 Z 坐标。 */
    private double lastOwnerZ = 0;

    /** 经过平滑的主人移动速度，单位格/tick。 */
    protected double ownerMovementSpeed = 0;

    /** 上一次更新移动跟踪所在的 tick。 */
    private int lastTrackingTick = -1;

    /**
     * 基于位置差值计算来更新主人移动跟踪。
     *
     * <p>为获得准确读数，必须每 tick 调用该方法。它使用位置跟踪，
     * 而不是 {@code getDeltaMovement()}，因为后者对玩家实体并不可靠
     * （即使正在行走也常常返回接近 0 的值）。
     *
     * <p>移动速度使用指数移动平均（当前值 20%，历史值 80%）进行平滑，
     * 以避免抖动影响静止状态的判定。
     */
    protected void updateOwnerMovementTracking() {
        double currentOwnerX = owner.getX();
        double currentOwnerZ = owner.getZ();
        int currentTick = petEntity.tickCount;
        int ticksSinceLastUpdate = currentTick - lastTrackingTick;
        lastTrackingTick = currentTick;

        // 仅在跟踪连续（无间隙）时才计算移动量
        if (ticksSinceLastUpdate <= MAX_TRACKING_GAP && ticksSinceLastUpdate > 0) {
            double dx = currentOwnerX - lastOwnerX;
            double dz = currentOwnerZ - lastOwnerZ;
            double currentMovement = Math.sqrt(dx * dx + dz * dz);
            if (ticksSinceLastUpdate > 1) {
                currentMovement /= ticksSinceLastUpdate;
            }
            ownerMovementSpeed = SPEED_SMOOTHING_RESPONSIVE * currentMovement + (1.0 - SPEED_SMOOTHING_RESPONSIVE) * ownerMovementSpeed;
        }
        lastOwnerX = currentOwnerX;
        lastOwnerZ = currentOwnerZ;

        // 若实际移动量非常小，则判定主人静止
        ownerStationary = ownerMovementSpeed < OWNER_STATIONARY_THRESHOLD;
    }

    /**
     * 为指定宠物创建新的随机漫步 AI goal。
     *
     * @param entityMyPet   将执行随机漫步的宠物实体
     * @param startDistance 漫步时与主人的最大距离（内部会求平方）
     */
    public MyPetRandomStroll(EntityMyPet entityMyPet, int startDistance) {
        this.petEntity = entityMyPet;
        this.nav = entityMyPet.getPetNavigation();
        this.startDistance = startDistance * startDistance;
        this.owner = ((CraftPlayer) petEntity.getOwner().getPlayer()).getHandle();
    }

    @Override
    public boolean shouldStart() {
        if (this.petEntity.getRandom().nextFloat() >= this.strollChance) {
            return false;
        }

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

        // 使用基于位置的跟踪判断主人是否静止
        // （getDeltaMovement 对玩家并不可靠 - 即使正在行走也返回接近 0 的值）
        updateOwnerMovementTracking();

        // 仅在主人确实静止时才允许漫步
        // 若主人在移动，宠物应当跟随，而不是漫步
        if (!ownerStationary) {
            return false;
        }

        double distSqToOwner = this.petEntity.distanceToSqr(owner);

        // 主人静止时，使用更严格的约束
        if (distSqToOwner <= STATIONARY_MAX_DIST_SQ) {
            // 只有在足够靠近主人且未被手动控制时才漫步
            return controlPathfinderGoal == null || controlPathfinderGoal.moveTo == null;
        } else return false;
    }

    @Override
    public boolean shouldFinish() {
        if (controlPathfinderGoal != null && controlPathfinderGoal.moveTo != null) {
            return true;
        } else if (this.petEntity.getOwner() == null) {
            return true;
        }

        // 使用基于位置的跟踪再次检查主人是否静止
        updateOwnerMovementTracking();

        // 主人静止时使用更严格的约束
        double maxDistSq = ownerStationary ? STATIONARY_MAX_DIST_SQ : this.startDistance;
        double distSqToOwner = this.petEntity.distanceToSqr(owner);

        if (distSqToOwner > maxDistSq) {
            return true;
        } else if (!this.petEntity.canMove()) {
            return true;
        } else if (moveTo == null) {
            return true;
        } else if (petEntity.getMyPet().getLocation()
                .map(loc -> MyPetApi.getPlatformHelper().distance(loc, moveTo) < DESTINATION_REACHED_THRESHOLD)
                .orElse(true)) {
            return true;
        } else if (timeToMove <= 0) {
            return true;
        } else if (this.petEntity.getMyPetTarget() != null && !this.petEntity.getMyPetTarget().isDead()) {
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        Vec3 vec = getPosition();
        if(vec == null) {
            return;
        }
        applySpeed();
        moveTo = new Location(this.petEntity.getBukkitEntity().getWorld(), vec.x, vec.y, vec.z);
        timeToMove = petEntity.getMyPet().getLocation()
                .map(loc -> (int) MyPetApi.getPlatformHelper().distance(loc, moveTo) / STROLL_TIMEOUT_DIVISOR)
                .orElse(MIN_STROLL_TIME);
        timeToMove = Math.max(timeToMove, MIN_STROLL_TIME);

        if(!nav.navigateTo(moveTo)) {
            this.moveTo = null;
        }
    }

    @Override
    public void finish() {
        nav.getParameters().removeSpeedModifier("RandomStroll");
        this.nav.stop();
    }

    /**
     * 为宠物生成一个随机漫步目标位置。
     *
     * <p>该位置需同时位于主人与宠物当前位置的可达范围内。
     * 当主人静止时，使用更严格的约束
     * （2 格半径 vs 3 格半径）。
     *
     * <p>该方法使用 {@link LandRandomPos} 最多尝试 5 个随机位置，
     * 若都不可用则返回 null。
     *
     * @return 有效的漫步目标位置；若找不到合适位置则返回 {@code null}
     */
    protected Vec3 getPosition() {
        // 主人静止时，使用更小的漫步半径
        int wanderRadius = ownerStationary ? WANDER_RADIUS_STATIONARY : WANDER_RADIUS_MOVING;
        double maxDistSqFromOwner = ownerStationary ? WANDER_MAX_DIST_SQ_STATIONARY : this.startDistance;
        // 同时限制宠物距其当前位置的行进距离
        double maxDistSqFromPet = ownerStationary ? WANDER_MAX_DIST_SQ_FROM_PET_STATIONARY : WANDER_MAX_DIST_SQ_FROM_PET_MOVING;

        Vec3 petPos = petEntity.position();

        // 获取一个随机位置，但确保其同时处于主人和宠物的范围内
        for (int i = 0; i < WANDER_MAX_ATTEMPTS; i++) {
            Vec3 vec = LandRandomPos.getPos(this.petEntity, wanderRadius, 0);
            if (vec != null) {
                // 检查目标到主人的距离
                double distSqToOwner = vec.distanceToSqr(owner.getX(), owner.getY(), owner.getZ());
                // 检查目标到宠物当前位置的距离
                double distSqToPet = vec.distanceToSqr(petPos);

                if (distSqToOwner < maxDistSqFromOwner && distSqToPet < maxDistSqFromPet) {
                    return vec;
                }
            }
        }
        // 若找不到有效位置，则停留在当前位置附近
        return null;
    }

    /**
     * 应用漫步时的移动速度。
     *
     * <p>使用缓慢的恒定速度（0.15），与玩家配置的行走速度无关。
     * 当玩家行走速度较高但仍站在原地时，这可避免宠物到处乱窜。
     *
     * <p>子类可以覆盖该方法，为飞行或游泳宠物提供不同的速度。
     */
    protected void applySpeed() {
        nav.getParameters().addSpeedModifier("RandomStroll", STROLL_SPEED);
    }

    /**
     * 漫步激活期间每 tick 调用，用于递减超时计数器。
     */
    public void schedule() {
        timeToMove--;
    }
}
