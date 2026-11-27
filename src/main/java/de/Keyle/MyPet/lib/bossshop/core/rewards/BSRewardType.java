package de.Keyle.MyPet.lib.bossshop.core.rewards;

import de.Keyle.MyPet.lib.bossshop.core.BSBuy;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public abstract class BSRewardType {
   public static BSRewardType Nothing;

   public static BSRewardType detectType(String var0) {
      return Nothing;
   }

   public void register() {
   }

   public abstract Object createObject(Object var1, boolean var2);

   public abstract boolean validityCheck(String var1, Object var2);

   public abstract void enableType();

   public abstract boolean canBuy(Player var1, BSBuy var2, boolean var3, Object var4, ClickType var5);

   public abstract void giveReward(Player var1, BSBuy var2, Object var3, ClickType var4);

   public abstract String getDisplayReward(Player var1, BSBuy var2, Object var3, ClickType var4);

   public abstract String[] createNames();

   public abstract boolean mightNeedShopUpdate();

   public boolean logTransaction() {
      return false;
   }
}
