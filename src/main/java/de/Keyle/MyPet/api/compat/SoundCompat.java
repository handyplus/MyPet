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

package de.Keyle.MyPet.api.compat;

import de.Keyle.MyPet.MyPetApi;
import org.bukkit.Location;
import org.bukkit.Sound;

import java.lang.reflect.Method;

public class SoundCompat {

    // 在类加载时解析一次：无论 org.bukkit.Sound 是枚举（<= 1.21.2）还是接口（1.21.3+），
    // 都保留了静态的 valueOf(String)，因此一次反射查找即可服务于所有受支持的服务端。
    // 仅当该方法不存在时才为 null。
    private static final Method SOUND_VALUE_OF = resolveSoundValueOf();

    private static Method resolveSoundValueOf() {
        try {
            return Sound.class.getMethod("valueOf", String.class);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Compat<String> ENDERMAN_TELEPORT = new Compat<String>()
            .d("mob.endermen.portal")
            .v("1.9", "entity.endermen.teleport")
            .v("1.13", "entity.enderman.teleport")
            .search();

    public static Compat<String> ITEM_PICKUP = new Compat<String>()
            .d("random.pop")
            .v("1.9", "entity.item.pickup")
            .search();

    public static Compat<String> THORNS_HIT = new Compat<String>()
            .d("damage.thorns")
            .v("1.9", "enchant.thorns.hit")
            .search();

    public static Compat<String> LEVEL_UP = new Compat<String>()
            .d("LEVEL_UP")
            .v("1.9", "ENTITY_PLAYER_LEVELUP")
            .search();

    public static Compat<String> FALL_BIG = new Compat<String>()
            .d("FALL_BIG")
            .v("1.9", "ENTITY_HOSTILE_BIG_FALL")
            .search();

    public static Compat<String> LEVEL_DOWN = new Compat<String>()
            .d("ANVIL_BREAK")
            .v("1.9", "ENTITY_WITHER_BREAK_BLOCK")
            .search();

    /**
     * 播放由与版本相关的 {@link Sound} 常量名解析出的音效
     * （即上面那些 {@code Compat<String>} 字段生成的值），同时不会把对
     * {@link Sound#valueOf(String)} 的静态引用固化进字节码中。
     * <p>
     * 随着 Minecraft 版本的演进，{@code org.bukkit.Sound} 的类型发生了改变：
     * 在约 1.21.2 及之前它是 {@code enum}，从 1.21.3 起则是 {@code interface}
     * （为保持源码兼容而保留了静态的 {@code valueOf}）。编译后的
     * {@code Sound.valueOf} 调用会携带它编译时所针对的 API 的常量池标签——
     * 枚举为 {@code Methodref}，接口为 {@code InterfaceMethodref}。当该标签
     * 与运行中服务端的 {@code Sound} 类型不一致时，JVM 在链接该调用时会抛出
     * {@link IncompatibleClassChangeError}。
     * <p>
     * 通过反射解析 {@code valueOf} 可以完全绕开静态调用，因此无论在运行时
     * {@code Sound} 是枚举还是接口，在所有受支持版本（1.8 至当前版本）上
     * 都能正确解析音效。
     *
     * @param location  播放音效的位置（若为 {@code null} 或没有世界则忽略）
     * @param soundName {@link Sound} 常量名，例如来自 {@link #LEVEL_UP}
     * @param volume    音效音量
     * @param pitch     音效音调
     */
    public static void play(Location location, String soundName, float volume, float pitch) {
        if (location == null || location.getWorld() == null || soundName == null || SOUND_VALUE_OF == null) {
            return;
        }
        try {
            Sound sound = (Sound) SOUND_VALUE_OF.invoke(null, soundName);
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (Throwable t) {
            // 该版本中未知的常量，或意外的链接错误：
            // 跳过该音效而不是中断外层事件，但保留对错误 Compat 映射的可诊断性，
            // 而不是静默失败。
            MyPetApi.getLogger().warning("Could not play sound '" + soundName + "': " + t);
        }
    }
}
