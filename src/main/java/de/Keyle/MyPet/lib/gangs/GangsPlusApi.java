package de.Keyle.MyPet.lib.gangs;

import java.util.ArrayList;
import java.util.List;
import de.Keyle.MyPet.lib.gangs.gang.Gang;
import org.bukkit.entity.Player;

public class GangsPlusApi {
   public static List<Gang> getAllGangs() {
      return new ArrayList();
   }

   public static boolean isInGang(Player player) {
      return false;
   }

   public static Gang getPlayersGang(Player player) {
      return null;
   }

   public static boolean isUsingGangChat(Player player) {
      return false;
   }

   public static boolean isUsingAllyChat(Player player) {
      return false;
   }
}
