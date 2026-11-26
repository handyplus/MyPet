package de.Keyle.MyPet.compat.v26_3_R1.util;

import de.Keyle.MyPet.MyPetApi;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Helper for NMS {@link ItemStack} → Bukkit conversions.
 * <p>
 * 26.3 moved {@code CraftItemStack.asBukkitCopy(ItemStack)} and {@code CraftItemStack.asCraftMirror(ItemStack)},
 * and Spigot and Paper moved them differently:
 * <ul>
 *     <li>Spigot keeps {@code asBukkitCopy(ItemStack)} and {@code asCraftMirror(ItemStack)}.</li>
 *     <li>Paper drops both and offers {@code asBukkitCopy(ItemInstance)} ({@link ItemStack} implements
 *     {@link ItemInstance}) and {@code asBukkitMirror(ItemStack)} instead.</li>
 * </ul>
 * A call compiled against one jar links to a descriptor the other jar doesn't have and fails at runtime with
 * {@link NoSuchMethodError}, so both conversions are resolved by name and parameter type once, at runtime.
 */
public final class CompatItemStack {

    /**
     * Type shared by all resolved handles: an NMS {@link ItemStack} in, a Bukkit {@link org.bukkit.inventory.ItemStack} out.
     */
    private static final MethodType BUKKIT_STACK = MethodType.methodType(org.bukkit.inventory.ItemStack.class, ItemStack.class);

    /**
     * Detached copy of an NMS stack. Null when the server offers no way to create one.
     */
    private static final MethodHandle BUKKIT_COPY;

    /**
     * Live Bukkit view of an NMS stack (no copy). Null when the server offers no way to create one.
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
            // No public copy overload: mirroring a fresh copy gives the same result
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
     * Resolves a public static factory of {@link CraftItemStack} with the given name and single parameter.
     *
     * @param lookup       lookup used to access the method
     * @param name         method name
     * @param parameterType exact parameter type the method must be declared with
     * @return the handle adapted to an NMS {@link ItemStack} argument and Bukkit item return type, or {@code null} when
     * the method is missing or not public
     */
    private static MethodHandle find(MethodHandles.Lookup lookup, String name, Class<?> parameterType) {
        try {
            return lookup.unreflect(CraftItemStack.class.getDeclaredMethod(name, parameterType)).asType(BUKKIT_STACK);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Converts an NMS {@link ItemStack} into a detached Bukkit copy.
     *
     * @param stack the NMS stack to copy
     * @return the Bukkit copy, or an empty item when the stack is empty
     * @throws IllegalStateException when the server offers no matching conversion or the conversion fails
     */
    public static org.bukkit.inventory.ItemStack asBukkitCopy(ItemStack stack) {
        return invoke(BUKKIT_COPY, stack);
    }

    /**
     * Wraps an NMS {@link ItemStack} in a live Bukkit view without copying it.
     *
     * @param stack the NMS stack to wrap
     * @return the Bukkit view of the stack
     * @throws IllegalStateException when the server offers no matching conversion or the conversion fails
     */
    public static org.bukkit.inventory.ItemStack asBukkitMirror(ItemStack stack) {
        return invoke(BUKKIT_MIRROR, stack);
    }

    /**
     * Invokes a resolved conversion handle.
     *
     * @param handle the conversion to use
     * @param stack  the NMS stack to convert
     * @return the Bukkit item produced by the conversion
     * @throws IllegalStateException when the handle is missing or the conversion fails
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
