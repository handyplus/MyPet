package de.Keyle.MyPet.api.util.hooks.types;

import de.Keyle.MyPet.api.player.MyPetPlayer;
import org.bukkit.entity.Entity;

/**
 * 此接口表示该 Hook 负责检查玩家是否试图通过骑乘宠物进入某个区域
 */
public interface MountInsideHook {
    /**
     * 返回 MyPet 玩家是否处于任何允许其拥有宠物的位置
     *
     * @param player MyPet 玩家
     * @return 玩家能否在该处骑乘宠物
     */
    boolean playerCanMount(MyPetPlayer player, Entity pet);
}
