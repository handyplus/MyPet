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

import com.google.common.collect.Iterables;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.compat.ParticleCompat;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.player.Permissions;
import de.Keyle.MyPet.api.skill.UpgradeComputer;
import de.Keyle.MyPet.api.skill.skills.Behavior;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.Keyle.MyPet.util.nbt.*;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static de.Keyle.MyPet.api.skill.skills.Behavior.BehaviorMode.*;

/**
 * 宠物 Behavior（行为）技能的实现。
 * <p>
 * 该组件管理当前可用的一组行为模式（activeBehaviors）、当前选中的模式
 * （selectedBehavior），以及一个循环迭代器（behaviorCycler），用于在激活的模式之间轮换。
 * 它还会响应升级回调，在运行时启用或禁用特定行为模式。
 * <p>
 * 健壮性：该实现保证选中的行为始终是当前激活的模式之一，并使用有界算法对齐内部迭代器，
 * 以避免无限循环。当请求一个不可用的模式时，会回退到
 * {@link de.Keyle.MyPet.api.skill.skills.Behavior.BehaviorMode#Normal}。
 */
public class BehaviorImpl implements Behavior {

    @Getter
    protected MyPet myPet;
    protected Set<BehaviorMode> activeBehaviors = new HashSet<>();
    protected BehaviorMode selectedBehavior = BehaviorMode.Normal;
    protected static Random random = new Random();
    Iterator<BehaviorMode> behaviorCycler;

    @Getter
    UpgradeComputer<Boolean> farmBehavior = new UpgradeComputer<>(false);
    @Getter
    UpgradeComputer<Boolean> raidBehavior = new UpgradeComputer<>(false);
    @Getter
    UpgradeComputer<Boolean> duelBehavior = new UpgradeComputer<>(false);
    @Getter
    UpgradeComputer<Boolean> aggressiveBehavior = new UpgradeComputer<>(false);
    @Getter
    UpgradeComputer<Boolean> friendlyBehavior = new UpgradeComputer<>(false);

    /**
     * 为指定宠物创建一个新的 Behavior 技能实例。
     *
     * @param myPet 所属宠物（不能为 null）
     */
    public BehaviorImpl(@NotNull MyPet myPet) {
        this.myPet = myPet;
        activeBehaviors.add(BehaviorMode.Normal);
        updateCycler();

        farmBehavior.addCallback((newValue, reason) -> setBehaviorMode(Farm, newValue));
        raidBehavior.addCallback((newValue, reason) -> setBehaviorMode(Raid, newValue));
        duelBehavior.addCallback((newValue, reason) -> setBehaviorMode(Duel, newValue));
        aggressiveBehavior.addCallback((newValue, reason) -> setBehaviorMode(Aggressive, newValue));
        friendlyBehavior.addCallback((newValue, reason) -> setBehaviorMode(Friendly, newValue));
    }

    /**
     * 指示该技能当前是否拥有除默认行为（Normal）之外的其他行为可用。
     *
     * @return 若除 Normal 外至少还有一种行为则返回 true；否则返回 false
     */
    public boolean isActive() {
        return activeBehaviors.size() > 1;
    }

    /**
     * 将 Behavior 技能重置为默认状态。
     * <p>
     * 清空所有激活的行为，只保留 Normal；选中行为设为 Normal，
     * 并重建、对齐内部循环迭代器。
     */
    @Override
    public void reset() {
        activeBehaviors.clear();
        activeBehaviors.add(BehaviorMode.Normal);
        selectedBehavior = BehaviorMode.Normal;
        updateCycler();
    }

    /**
     * 基于当前激活的模式重建并对齐内部行为循环迭代器。
     * <p>
     * 先复制并排序激活的模式，以获得确定性的迭代顺序。然后校验选中的行为
     * 确保其包含在内；否则回退到 Normal，必要时回退到第一个可用项。
     * 最后以有界方式将迭代器对齐到选中的行为，避免无界循环。
     */
    protected void updateCycler() {
        List<BehaviorMode> activeBehaviors = new ArrayList<>(this.activeBehaviors);
        Collections.sort(activeBehaviors);

        // 确保 selectedBehavior 有效
        if (!activeBehaviors.contains(selectedBehavior)) {
            selectedBehavior = BehaviorMode.Normal;
            if (!activeBehaviors.contains(selectedBehavior) && !activeBehaviors.isEmpty()) {
                selectedBehavior = activeBehaviors.get(0);
            }
        }

        behaviorCycler = Iterables.cycle(activeBehaviors).iterator();

        // 以有界方式将迭代器位置对齐到 selectedBehavior
        for (int i = 0; i < activeBehaviors.size(); i++) {
            if (behaviorCycler.next() == selectedBehavior) {
                break;
            }
        }
    }

    /**
     * 为该宠物选中给定的行为模式。
     * <p>
     * 注意：选中的模式必须存在于激活的行为集合中，否则内部对齐可能出现异常。
     * 本类的当前实现通过在 {@link #updateCycler()} 中校验并对齐来保证安全。
     *
     * @param mode 要选中的行为模式（不能为 null）
     */
    @Override
    public void setBehavior(@NotNull BehaviorMode mode) {
        // 若请求的模式当前不可用，则回退到 Normal
        if (!activeBehaviors.contains(mode)) {
            mode = BehaviorMode.Normal;
        }
        selectedBehavior = mode;
        // 重建并安全地对齐循环迭代器
        updateCycler();
    }

    /**
     * 启用或禁用特定行为模式，并相应地更新循环迭代器。
     * <p>
     * 若在某个模式被选中时将其禁用，选中项会回退到 Normal。
     *
     * @param mode  要更改的行为模式
     * @param value true 表示启用（使其可用）；false 表示禁用
     */
    protected void setBehaviorMode(@NotNull BehaviorMode mode, boolean value) {
        if (value) {
            activeBehaviors.add(mode);
        } else {
            activeBehaviors.remove(mode);
            if (mode == selectedBehavior) {
                selectedBehavior = BehaviorMode.Normal;
            }
        }
        updateCycler();
    }

    /**
     * 获取当前选中的行为模式。
     *
     * @return 当前的行为模式（永不为 null）
     */
    @Override
    public @NotNull BehaviorMode getBehavior() {
        return selectedBehavior;
    }

    /**
     * 检查给定的行为模式对该宠物当前是否可用（即已激活）。
     *
     * @param mode 要检查的模式（不能为 null）
     * @return 若该模式存在于激活的行为集合中则返回 true；否则返回 false
     */
    public boolean isModeUsable(@NotNull BehaviorMode mode) {
        return activeBehaviors.contains(mode);
    }

    /**
     * 返回给定语言下所有当前激活的行为模式的可读列表。
     *
     * @param locale 用于翻译的语言键（不能为 null）
     * @return 描述激活行为模式的格式化字符串
     */
    public @NotNull String toPrettyString(@NotNull String locale) {
        String activeModes = ChatColor.GOLD + Translation.getString("Name.Normal", locale) + ChatColor.RESET;
        if (activeBehaviors.contains(Friendly)) {
            activeModes += ", " + ChatColor.GOLD + Translation.getString("Name.Friendly", locale) + ChatColor.RESET;
        }
        if (activeBehaviors.contains(Aggressive)) {
            if (!activeModes.equalsIgnoreCase("")) {
                activeModes += ", ";
            }
            activeModes += ChatColor.GOLD + Translation.getString("Name.Aggressive", locale) + ChatColor.RESET;
        }
        if (activeBehaviors.contains(Farm)) {
            if (!activeModes.equalsIgnoreCase("")) {
                activeModes += ", ";
            }
            activeModes += ChatColor.GOLD + Translation.getString("Name.Farm", locale) + ChatColor.RESET;
        }
        if (activeBehaviors.contains(Raid)) {
            if (!activeModes.equalsIgnoreCase("")) {
                activeModes += ", ";
            }
            activeModes += ChatColor.GOLD + Translation.getString("Name.Raid", locale) + ChatColor.RESET;
        }
        if (activeBehaviors.contains(Duel)) {
            if (!activeModes.equalsIgnoreCase("")) {
                activeModes += ", ";
            }
            activeModes += ChatColor.GOLD + Translation.getString("Name.Duel", locale) + ChatColor.RESET;
        }
        return Translation.getString("Name.Modes", locale) + ": " + activeModes;
    }

    /**
     * 生成当 Behavior 技能新增或改变了可用模式时展示给宠物主人的本地化升级消息。
     *
     * @return 两行的本地化消息数组（永不为 null）
     */
    @Override
    public @NotNull String[] getUpgradeMessage() {
        return new String[]{
                Util.formatText(Translation.getString("Message.Skill.Behavior.Upgrade", myPet.getOwner().getLanguage()), myPet.getPetName()),
                "  " + toPrettyString(myPet.getOwner().getLanguage())
        };
    }

    /**
     * 循环切换到下一个可用的行为模式，并通知宠物主人。
     * <p>
     * 若存在其他可用模式，循环迭代器会前进，直到找到一个 Normal 模式或玩家
     * 拥有对应权限的模式。随后更新选中项，并向主人发送本地化聊天消息。
     *
     * @return 若模式发生变化或可被确认则返回 true；若该技能只有 Normal 则返回 false
     */
    public boolean activate() {
        if (isActive()) {
            while (true) {
                selectedBehavior = behaviorCycler.next();
                if (selectedBehavior != Normal) {
                    if (Permissions.has(myPet.getOwner().getPlayer(), "MyPet.extended.behavior." + selectedBehavior.name().toLowerCase())) {
                        break;
                    }
                } else {
                    break;
                }
            }
            myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skill.Behavior.NewMode", myPet.getOwner()), myPet.getPetName(), Translation.getString("Name." + selectedBehavior.name(), myPet.getOwner().getPlayer())));
            return true;
        } else {
            myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.No.Skill", myPet.getOwner()), myPet.getPetName(), this.getName(myPet.getOwner().getLanguage())));
            return false;
        }
    }

    /**
     * Scheduler 接口提供的周期性 tick 钩子。
     * <p>
     * 当当前模式为 Aggressive 且宠物在场（Here）时，偶尔在宠物头顶发射
     * 装饰性的愤怒村民粒子效果。
     */
    @Override
    public void schedule() {
        if (selectedBehavior == Aggressive && random.nextBoolean() && myPet.getStatus() == MyPet.PetState.Here) {
            myPet.getEntity().ifPresent(entity -> MyPetApi.getPlatformHelper()
                    .playParticleEffect(entity.getLocation().add(0, entity.getEyeHeight(), 0), ParticleCompat.VILLAGER_ANGRY.get(), 0.2F, 0.2F, 0.2F, 0.5F, 1, 20));
        }
    }

    /**
     * 包含激活行为集合与当前选中模式的调试字符串。
     */
    @Override
    public String toString() {
        return "BehaviorImpl{" +
                "activeBehaviors=" + activeBehaviors +
                ", selectedBehavior=" + selectedBehavior +
                '}';
    }

    /**
     * 将该技能的当前状态序列化到 NBT。
     * <p>
     * 目前仅以 "selectedBehavior" 为键保存所选行为的名称。
     *
     * @return 包含该技能持久化数据的复合标签（永不为 null）
     */
    @Override
    public @NotNull TagCompound save() {
        TagCompound nbtTagCompound = new TagCompound();
        nbtTagCompound.getCompoundData().put("selectedBehavior", new TagString(this.selectedBehavior.name()));
        return nbtTagCompound;
    }

    /**
     * 从 NBT 反序列化保存的状态并恢复所选行为。
     * <p>
     * 未知或当前不可用的模式会映射为 Normal，以保证状态有效。
     *
     * @param tagCompound 该技能的 NBT 数据（不能为 null）
     */
    @Override
    public void load(@NotNull TagCompound tagCompound) {
        if (tagCompound.containsKey("selectedBehavior")) {
            String behaviorString = tagCompound.getAs("selectedBehavior", TagString.class).getStringData();
            switch (behaviorString) {
                case "Friendly":
                    setBehavior(Friendly);
                    break;
                case "Aggressive":
                    setBehavior(Aggressive);
                    break;
                case "Raid":
                    setBehavior(Raid);
                    break;
                case "Farm":
                    setBehavior(Farm);
                    break;
                case "Duel":
                    setBehavior(Duel);
                    break;
                default:
                    setBehavior(Normal);
                    break;
            }
        }
    }
}