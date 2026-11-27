package de.Keyle.MyPet.compat.v26_3_R1.util;

import de.Keyle.MyPet.MyPetApi;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * NMS {@link ItemStack} → Bukkit 转换的辅助类。
 * <p>
 * 26.3 迁移了 {@code CraftItemStack.asBukkitCopy(ItemStack)} 和 {@code CraftItemStack.asCraftMirror(ItemStack)}，
 * 而 Spigot 和 Paper 的迁移方式并不相同：
 * <ul>
 *     <li>Spigot 保留 {@code asBukkitCopy(ItemStack)} 和 {@code asCraftMirror(ItemStack)}。</li>
 *     <li>Paper 去掉了这两个方法，改为提供 {@code asBukkitCopy(ItemInstance)}（{@link ItemStack} 实现了
 *     {@link ItemInstance}）和 {@code asBukkitMirror(ItemStack)}。</li>
 * </ul>
 * 针对其中一个 jar 编译的调用会链接到另一个 jar 并不存在的描述符，并在运行时抛出
 * {@link NoSuchMethodError} 而失败，因此两种转换都在运行时按名称和参数类型解析一次。
 */
public final class CompatItemStack {

    /**
     * 所有已解析句柄共享的类型：输入一个 NMS {@link ItemStack}，输出一个 Bukkit {@link org.bukkit.inventory.ItemStack}。
     */
    private static final MethodType BUKKIT_STACK = MethodType.methodType(org.bukkit.inventory.ItemStack.class, ItemStack.class);

    /**
     * NMS 物品堆的独立副本。服务器没有可用的创建方式时为 null。
     */
    private static final MethodHandle BUKKIT_COPY;

    /**
     * NMS 物品堆的实时 Bukkit 视图（不复制）。服务器没有可用的创建方式时为 null。
     */
    private static final MethodHandle BUKKIT_MIRROR;

    static {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle copy = find(lookup, "asBukkitCopy", ItemInstance.class); // Paper
        if (copy == null) {
            copy = find(lookup, "asBukkitCopy", ItemStack.class); // Spigot
        }
        MethodHandle mirror = find(lookup, "asBukkitMirror", ItemStack.class); // Paper
        if (mirror == null) {
            mirror = find(lookup, "asCraftMirror", ItemStack.class); // Spigot
        }
        if (copy == null && mirror != null) {
            // 没有公开的复制重载：对一个新副本做镜像可以得到相同结果
            try {
                copy = MethodHandles.filterArguments(mirror, 0,
                        lookup.findVirtual(ItemStack.class, "copy", MethodType.methodType(ItemStack.class)));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                MyPetApi.getLogger().warning("Could not link ItemStack.copy(): " + e);
            }
        }
        if (copy == null || mirror == null) {
            MyPetApi.getLogger().warning("This server provides no usable CraftItemStack conversion from NMS to Bukkit items"
                    + " (copy: " + (copy != null) + ", mirror: " + (mirror != null) + ")");
        }
        BUKKIT_COPY = copy;
        BUKKIT_MIRROR = mirror;
    }

    private CompatItemStack() {}

    /**
     * 解析 {@link CraftItemStack} 中具有给定名称和单个参数的公开静态工厂方法。
     *
     * @param lookup       用于访问该方法的 lookup
     * @param name         方法名
     * @param parameterType 该方法必须以该类型声明的精确参数类型
     * @return 已适配为接收 NMS {@link ItemStack} 参数并返回 Bukkit 物品类型的句柄；方法缺失或不是公开方法时返回 {@code null}
     */
    private static MethodHandle find(MethodHandles.Lookup lookup, String name, Class<?> parameterType) {
        try {
            return lookup.unreflect(CraftItemStack.class.getDeclaredMethod(name, parameterType)).asType(BUKKIT_STACK);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * 将 NMS {@link ItemStack} 转换为独立的 Bukkit 副本。
     *
     * @param stack 要复制的 NMS 物品堆
     * @return Bukkit 副本；物品堆为空时返回空物品
     * @throws IllegalStateException 服务器没有匹配的转换方法或转换失败时抛出
     */
    public static org.bukkit.inventory.ItemStack asBukkitCopy(ItemStack stack) {
        return invoke(BUKKIT_COPY, stack);
    }

    /**
     * 将 NMS {@link ItemStack} 包装为实时 Bukkit 视图，不进行复制。
     *
     * @param stack 要包装的 NMS 物品堆
     * @return 该物品堆的 Bukkit 视图
     * @throws IllegalStateException 服务器没有匹配的转换方法或转换失败时抛出
     */
    public static org.bukkit.inventory.ItemStack asBukkitMirror(ItemStack stack) {
        return invoke(BUKKIT_MIRROR, stack);
    }

    /**
     * 调用一个已解析的转换句柄。
     *
     * @param handle 要使用的转换
     * @param stack  要转换的 NMS 物品堆
     * @return 转换得到的 Bukkit 物品
     * @throws IllegalStateException 句柄缺失或转换失败时抛出
     */
    private static org.bukkit.inventory.ItemStack invoke(MethodHandle handle, ItemStack stack) {
        if (handle == null) {
            throw new IllegalStateException("This server provides no NMS to Bukkit ItemStack conversion");
        }
        try {
            return (org.bukkit.inventory.ItemStack) handle.invokeExact(stack);
        } catch (Throwable t) {
            throw new IllegalStateException("NMS to Bukkit ItemStack conversion failed", t);
        }
    }
}
