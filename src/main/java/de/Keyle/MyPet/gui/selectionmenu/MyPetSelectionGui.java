/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2019 Keyle
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

package de.Keyle.MyPet.gui.selectionmenu;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.WorldGroup;
import de.Keyle.MyPet.api.entity.StoredMyPet;
import de.Keyle.MyPet.api.gui.IconMenu;
import de.Keyle.MyPet.api.gui.IconMenuItem;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.repository.RepositoryCallback;
import de.Keyle.MyPet.api.util.Colorizer;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.Keyle.MyPet.api.util.service.types.EggIconService;
import de.Keyle.MyPet.util.translation.PetDefaultNameResolver;
import de.Keyle.MyPet.util.nbt.TagCompound;
import de.Keyle.MyPet.util.nbt.TagInt;

import java.util.*;

import static org.bukkit.ChatColor.GOLD;
import static org.bukkit.ChatColor.RESET;

public class MyPetSelectionGui {

    private final MyPetPlayer player;
    private final String title;
    private final int page;

    public MyPetSelectionGui(MyPetPlayer player, String title) {
        this.player = player;
        this.title = title;
        this.page = 1;
    }

    public MyPetSelectionGui(MyPetPlayer player, String title, int page) {
        this.player = player;
        this.title = title;
        this.page = page;
    }

    public void open(List<StoredMyPet> pets, final RepositoryCallback<StoredMyPet> callback) {
        List<StoredMyPet> pagedPets;
        // 限制每页宠物数量为 54
        int startIndex = (page - 1) * 54;
        int endIndex = Math.min(startIndex + 54, pets.size());
        if (startIndex < pets.size()) {
            pagedPets = pets.subList(startIndex, endIndex);
        } else {
            pagedPets = pets.subList(0, Math.min(54, pets.size()));
        }


        final Map<Integer, StoredMyPet> petSlotList = new HashMap<>();
        WorldGroup wg = WorldGroup.getGroupByWorld(player.getPlayer().getWorld().getName());

        IconMenu menu = new IconMenu(title, event -> {
            if (petSlotList.containsKey(event.getPosition())) {
                StoredMyPet storedMyPet = petSlotList.get(event.getPosition());
                if (storedMyPet != null && callback != null) {
                    // 菜单显示的是打开时的宠物状态。在使用被点击的宠物之前需要重新加载，
                    // 因为此后它可能以不同内容被保存、被交易给其他玩家、被释放或在别处被激活。
                    // getMyPet 会等待待处理的保存，并在主线程上返回结果。
                    MyPetApi.getRepository().getMyPet(storedMyPet.getUUID(), new RepositoryCallback<StoredMyPet>() {
                        @Override
                        public void callback(StoredMyPet current) {
                            if (current == null || !current.getOwner().getInternalUUID().equals(player.getInternalUUID()) || !player.isOnline()) {
                                return;
                            }
                            if (player.hasMyPet() && player.getMyPet().getUUID().equals(current.getUUID())) {
                                return;
                            }
                            callback.callback(current);
                        }
                    });
                }
            }

            event.setWillClose(true);
            event.setWillDestroy(true);
        }, MyPetApi.getPlugin()).setPaginationIdentifier("SelectMyPet");

        int nextPosition = 0;

        for (StoredMyPet currentPet : pagedPets) {
            if (currentPet.getWorldGroup().isEmpty() || !currentPet.getWorldGroup().equals(wg.getName()))
                continue;

            if (player.hasMyPet() && player.getMyPet().getUUID().equals(currentPet.getUUID()))
                continue;

            List<String> lore = new ArrayList<>();

            if (Configuration.HungerSystem.USE_HUNGER_SYSTEM)
                lore.add(RESET + Translation.getString("Name.Hunger", player) + ": " + GOLD + Math.round(currentPet.getSaturation()));

            if (currentPet.getRespawnTime() > 0) {
                lore.add(RESET + Translation.getString("Name.Respawntime", player) + ": " + GOLD + currentPet.getRespawnTime() + "sec");
            } else {
                lore.add(RESET + Translation.getString("Name.HP", player) + ": " + GOLD + String.format("%1.2f", currentPet.getHealth()));
            }

            boolean levelFound = false;
            if (currentPet.getInfo().containsKey("storage")) {
                TagCompound storage = currentPet.getInfo().getAs("storage", TagCompound.class);
                if (storage.containsKey("level")) {
                    lore.add(RESET + Translation.getString("Name.Level", player) + ": " + GOLD + storage.getAs("level", TagInt.class).getIntData());
                    levelFound = true;
                }
            }

            if (!levelFound)
                lore.add(RESET + Translation.getString("Name.Exp", player) + ": " + GOLD + String.format("%1.2f", currentPet.getExp()));

            lore.add(RESET + Translation.getString("Name.Type", player) + ": " + GOLD + PetDefaultNameResolver.resolve(currentPet.getPetType(), player));
            lore.add(RESET + Translation.getString("Name.Skilltree", player) + ": " + GOLD + Colorizer.setColors(currentPet.getSkilltree() != null ? currentPet.getSkilltree().getDisplayName() : "-"));

            IconMenuItem icon = new IconMenuItem();
            icon.setTitle(RESET + currentPet.getPetName());
            icon.addLore(lore);
            Optional<EggIconService> egg = MyPetApi.getServiceManager().getService(EggIconService.class);
            egg.ifPresent(service -> service.updateIcon(currentPet.getPetType(), icon));

            int currentPosition = nextPosition++;

            menu.setOption(currentPosition, icon);
            petSlotList.put(currentPosition, currentPet);
        }
        menu.open(player.getPlayer());
    }
}