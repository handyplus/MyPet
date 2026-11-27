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

package de.Keyle.MyPet.skill.skills;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.compat.ParticleCompat;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.skill.UpgradeComputer;
import de.Keyle.MyPet.api.skill.skills.Bleed;
import de.Keyle.MyPet.api.util.locale.Translation;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BleedImpl implements Bleed {

    private static Random random = new Random();

    @Getter
    private MyPet myPet;

    @Getter
    protected UpgradeComputer<Number> damage = new UpgradeComputer<>(0);
    @Getter
    protected UpgradeComputer<Integer> interval = new UpgradeComputer<>(1);
    @Getter
    protected UpgradeComputer<Integer> duration = new UpgradeComputer<>(0);
    @Getter
    protected UpgradeComputer<Integer> chance = new UpgradeComputer<>(0);

    private final Map<UUID, BleedEffect> activeEffects = new ConcurrentHashMap<>();
    private BukkitTask bleedTask = null;
    private boolean ticking = false;

    public BleedImpl(MyPet myPet) {
        this.myPet = myPet;
    }

    @Override
    public boolean isActive() {
        return chance.getValue() > 0 && duration.getValue() > 0 && damage.getValue().doubleValue() > 0;
    }

    @Override
    public void reset() {
        damage.removeAllUpgrades();
        interval.removeAllUpgrades();
        duration.removeAllUpgrades();
        chance.removeAllUpgrades();
        activeEffects.clear();
        stopBleedTask();
    }

    @Override
    public String toPrettyString(String locale) {
        return "" + ChatColor.GOLD + chance.getValue() + ChatColor.RESET + "% -> "
                + ChatColor.GOLD + damage.getValue().doubleValue() + ChatColor.RESET + " "
                + Translation.getString("Name.Damage", locale) + "/"
                + ChatColor.GOLD + interval.getValue() + ChatColor.RESET + "s for "
                + ChatColor.GOLD + duration.getValue() + ChatColor.RESET + " "
                + Translation.getString("Name.Seconds", locale);
    }

    @Override
    public String[] getUpgradeMessage() {
        return new String[]{
                Util.formatText(
                        Translation.getString("Message.Skill.Bleed.Upgrade", myPet.getOwner().getLanguage()),
                        myPet.getPetName(),
                        getChance().getValue(),
                        getDamage().getValue().doubleValue(),
                        getInterval().getValue(),
                        getDuration().getValue()
                )
        };
    }

    @Override
    public boolean trigger() {
        // 流血持续伤害的每一次 tick 都算作宠物造成的攻击；它本身不得触发或延长流血效果。
        if (ticking) {
            return false;
        }
        return random.nextDouble() <= chance.getValue() / 100.;
    }

    @Override
    public void apply(LivingEntity target) {
        UUID targetId = target.getUniqueId();
        BleedEffect existing = activeEffects.get(targetId);

        double damageValue = damage.getValue().doubleValue();
        int intervalTicks = interval.getValue() * 20;

        if (existing != null) {
            // 刷新并延长：把剩余持续时间加到新的持续时间上
            // 总时长绝不超过配置持续时间的两倍，从而多次命中也不会让
            // 目标无限期地持续流血。
            int remainingSeconds = existing.remainingTicks / 20;
            int newDurationSeconds = Math.min(duration.getValue() + remainingSeconds, duration.getValue() * 2);
            existing.refresh(target, damageValue, intervalTicks, newDurationSeconds * 20);
        } else {
            // 创建新的流血效果并立即造成第一次伤害
            BleedEffect effect = new BleedEffect(target, damageValue, intervalTicks, duration.getValue() * 20);
            activeEffects.put(targetId, effect);

            // 命中时立即造成第一次流血伤害
            LivingEntity petEntity = myPet.getEntity().map(e -> (LivingEntity) e).orElse(null);
            effect.applyDamage(petEntity);
        }

        // 若流血任务尚未启动则启动它
        startBleedTask();
    }

    private void startBleedTask() {
        if (bleedTask != null) {
            return;
        }

        bleedTask = new BukkitRunnable() {
            @Override
            public void run() {
                processBleedEffects();

                // 若已无任何流血效果则停止任务
                if (activeEffects.isEmpty()) {
                    stopBleedTask();
                }
            }
        }.runTaskTimer(MyPetApi.getPlugin(), 20L, 20L);
    }

    private void stopBleedTask() {
        if (bleedTask != null) {
            bleedTask.cancel();
            bleedTask = null;
        }
    }

    private void processBleedEffects() {
        // 获取宠物实体（若宠物已死亡则可能为 null）
        LivingEntity petEntity = myPet.getEntity().map(e -> (LivingEntity) e).orElse(null);

        Iterator<Map.Entry<UUID, BleedEffect>> iterator = activeEffects.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, BleedEffect> entry = iterator.next();
            BleedEffect effect = entry.getValue();

            // 检查目标是否仍然有效
            if (!effect.isTargetValid()) {
                iterator.remove();
                continue;
            }

            // 检查该效果是否已过期
            if (effect.isExpired()) {
                iterator.remove();
                continue;
            }

            // 每个 tick 都要重新检查保护状态：目标可能在命中之后进入了关闭 PvP 的或
            // 受保护的区域，在那里宠物无法攻击它。
            if (!SkillDamageCheck.canDamage(myPet, effect.target)) {
                iterator.remove();
                continue;
            }

            // 处理一次 tick（若宠物已死亡则 petEntity 可能为 null —— 此时伤害继续但不归属宠物）
            ticking = true;
            try {
                effect.tick(petEntity);
            } finally {
                ticking = false;
            }
        }
    }

    @Override
    public void schedule() {
        // 流血使用自己的 Bukkit 任务，以便在宠物死亡后仍能持续
        // 此方法仅为满足接口要求而保留，不做任何事
    }

    @Override
    public String toString() {
        return "BleedImpl{" +
                "damage=" + damage.getValue().doubleValue() +
                ", interval=" + interval.getValue() +
                ", duration=" + duration.getValue() +
                ", chance=" + chance.getValue() +
                ", activeEffects=" + activeEffects.size() +
                '}';
    }

    /**
     * 内部类，表示目标实体上正在生效的流血效果
     */
    private static class BleedEffect {
        private LivingEntity target;
        private double damagePerTick;
        private int intervalTicks;
        private int remainingTicks;
        private int ticksSinceLastDamage;

        public BleedEffect(LivingEntity target, double damagePerTick, int intervalTicks, int durationTicks) {
            this.target = target;
            this.damagePerTick = damagePerTick;
            this.intervalTicks = intervalTicks;
            this.remainingTicks = durationTicks;
            this.ticksSinceLastDamage = 0; // 首次伤害在 apply() 中施加，因此重置间隔计时
        }

        public void refresh(LivingEntity target, double newDamage, int newIntervalTicks, int newDurationTicks) {
            this.target = target;
            this.damagePerTick = newDamage;
            this.intervalTicks = newIntervalTicks;
            this.remainingTicks = newDurationTicks;
            // 不重置 ticksSinceLastDamage，以维持伤害节奏
        }

        public void tick(LivingEntity petEntity) {
            remainingTicks -= 20; // 每秒调用一次（20 ticks）
            ticksSinceLastDamage += 20;

            if (ticksSinceLastDamage >= intervalTicks) {
                applyDamage(petEntity);
                ticksSinceLastDamage = 0;
            }
        }

        private void applyDamage(LivingEntity petEntity) {
            if (!isTargetValid()) {
                return;
            }

            // 施加伤害 - 若有宠物则归属到宠物，否则只造成无归属的原始伤害
            if (petEntity != null) {
                target.damage(damagePerTick, petEntity);
            } else {
                target.damage(damagePerTick);
            }

            // 播放红色方块粒子以模拟血液效果
            MyPetApi.getPlatformHelper().playParticleEffect(
                    target.getLocation().add(0, 1, 0),
                    ParticleCompat.BLOCK_CRACK.get(),
                    0.3f, 0.5f, 0.3f,
                    0.1f,
                    10,
                    16,
                    ParticleCompat.REDSTONE_BLOCK_DATA
            );
        }

        public boolean isExpired() {
            return remainingTicks <= 0;
        }

        public boolean isTargetValid() {
            return target != null && target.isValid() && !target.isDead();
        }
    }
}