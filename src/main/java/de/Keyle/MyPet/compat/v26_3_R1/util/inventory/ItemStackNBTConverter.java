/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2020 Keyle
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

package de.Keyle.MyPet.compat.v26_3_R1.util.inventory;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.util.Compat;
import de.Keyle.MyPet.api.util.inventory.ItemDataVersion;
import de.Keyle.MyPet.api.util.ReflectionUtil;
import de.Keyle.MyPet.util.nbt.TagType;
import de.Keyle.MyPet.util.nbt.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import de.Keyle.MyPet.compat.v26_3_R1.util.CompatItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Compat("v26_3_R1")
public class ItemStackNBTConverter {

	private static final Field TAG_LIST_LIST = ReflectionUtil.getField(ListTag.class, "list");
    public static RegistryAccess registryAccess = CraftRegistry.getMinecraftRegistry();
    private static final CompoundTag EMPTY_ITEM_COMPOUND;

    static {
        EMPTY_ITEM_COMPOUND = new CompoundTag();
        EMPTY_ITEM_COMPOUND.putString("id", "minecraft:air");
        EMPTY_ITEM_COMPOUND.putInt("count", 1);
    }

    public static TagCompound itemStackToCompound(org.bukkit.inventory.ItemStack itemStack) {
        return itemStackToCompound(CraftItemStack.asNMSCopy(itemStack));
    }

    public static TagCompound itemStackToCompound(ItemStack itemStack) {
        // 记录写入该物品的数据版本（见 ItemDataVersion）。只有用于保存的 compound 会被打上标记；
        // itemStackToVanillaCompound 保持不打标记，因为物品比较和配置物品会使用它。
        return ItemDataVersion.stamp((TagCompound) vanillaCompoundToCompound(itemStackToVanillaCompound(itemStack)), currentDataVersion());
    }

    public static CompoundTag itemStackToVanillaCompound(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return EMPTY_ITEM_COMPOUND.copy();
        }

        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registryAccess);
        DataResult<Tag> res = ItemStack.CODEC.encodeStart(ops, itemStack);
        Tag out = res.result().orElseGet(CompoundTag::new);

        // 对 ItemStack 而言这会是一个 CompoundTag
        return (out instanceof CompoundTag ct) ? ct : new CompoundTag();
    }

    public static ItemStack compoundToItemStack(TagCompound compound) {
        int stored = ItemDataVersion.read(compound);
        if (stored != ItemDataVersion.NONE) {
            // 带标记的物品：从保存它的版本开始运行数据修复器，然后在不带标记的情况下解析它。
            // 如果出现任何问题，则使用下面未做改动的路径。
            try {
                CompoundTag tag = (CompoundTag) compoundToVanillaCompound(ItemDataVersion.withoutStamp(compound));
                int server = currentDataVersion();
                int start = ItemDataVersion.startVersion(stored, server);
                if (start < server) {
                    tag = convertOldVanillaCompound(tag, start);
                }
                ItemStack stack = vanillaCompoundToItemStack(tag);
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            } catch (RuntimeException ignored) {
                // 回退到下面未做改动的路径
            }
            compound = ItemDataVersion.withoutStamp(compound);
        }
        CompoundTag tagCompound = (CompoundTag) compoundToVanillaCompound(compound);
        return vanillaCompoundToItemStack(tagCompound);
    }

    public static org.bukkit.inventory.ItemStack compoundToBukkitItemStack(TagCompound compound) {
        return CompatItemStack.asBukkitCopy(compoundToItemStack(compound));
    }

    public static ItemStack vanillaCompoundToItemStack(CompoundTag compoundTag) {
        if (compoundTag == null || compoundTag.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 快速检查是否为空气（防御性）
        Optional<String> id = compoundTag.getString("id"); // 缺失时返回 ""
        if (id.isEmpty() || "minecraft:air".equals(id.get()) || "air".equals(id.get())) {
            return ItemStack.EMPTY;
        }

        CompoundTag toParse = compoundTag;
        boolean modified = false;

        // 检查是否带有旧的 "tag" 格式，需要 DataFixer 转换
        if (toParse.contains("tag")) {
            toParse = convertOldVanillaCompound(toParse);
            modified = true;
        }

        // 如果有 components 但使用了旧的中间格式，则修复它们
        else if (toParse.contains("components")) {
            CompoundTag fixed = fixEnchantmentsFormat(toParse);
            if (!fixed.equals(toParse)) {
                toParse = fixed;
                modified = true;
            }
        }

        // 如果发生了转换，则输出修正后的字符串
        if (modified) {
            MyPetApi.getLogger().warning("Old item format detected! Update your item to the new format (make sure to include the period at the beginning):");
            MyPetApi.getLogger().warning(" . " + toParse);
            MyPetApi.getLogger().warning("This warning will disappear once you update your config.");
        }

        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registryAccess);

        DataResult<ItemStack> parseResult = ItemStack.CODEC.parse(ops, toParse);
        if (parseResult.error().isPresent()) {
            MyPetApi.getLogger().warning("Failed to parse ItemStack: " + parseResult.error().get().message());
        }
        return parseResult.result().orElse(ItemStack.EMPTY);
    }

    private static CompoundTag fixEnchantmentsFormat(CompoundTag compoundTag) {
        CompoundTag result = compoundTag.copy();

        // 检查是否存在 components 且其附魔为旧格式
        if (result.contains("components")) {
            Optional<CompoundTag> componentsOpt = result.getCompound("components");
            if (componentsOpt.isEmpty()) {
                return result;
            }
            CompoundTag components = componentsOpt.get().copy();
            boolean modified = false;

            // 修复附魔格式
            if (components.contains("minecraft:enchantments")) {
                Optional<CompoundTag> enchantmentsOpt = components.getCompound("minecraft:enchantments");
                if (enchantmentsOpt.isPresent()) {
                    CompoundTag enchantments = enchantmentsOpt.get();

                    // 检查是否使用包含 "levels" 和 "show_in_tooltip" 的旧格式
                    if (enchantments.contains("levels")) {
                        Optional<CompoundTag> levelsOpt = enchantments.getCompound("levels");
                        if (levelsOpt.isPresent()) {
                            CompoundTag levels = levelsOpt.get();
                            components.put("minecraft:enchantments", levels);
                            modified = true;
                        }
                    }
                }
            }

            // 修复以字符串形式存储的文本组件（custom_name、lore 等）
            modified |= fixTextComponent(components, "minecraft:custom_name");
            modified |= fixTextComponentList(components, "minecraft:lore");

            if (modified) {
                result.put("components", components);
            }
        }

        return result;
    }

    private static boolean fixTextComponent(CompoundTag components, String key) {
        if (components.contains(key)) {
            Optional<String> textOpt = components.getString(key);
            if (textOpt.isPresent()) {
                String text = textOpt.get();
                // 检查是否为需要解析的 JSON 字符串
                if (text.startsWith("{") && text.endsWith("}")) {
                    try {
                        CompoundTag parsed = TagParser.parseCompoundFully(text);
                        components.put(key, parsed);
                        return true;
                    } catch (Exception e) {
                        MyPetApi.getLogger().warning("Failed to parse text component for " + key + ": " + e.getMessage());
                    }
                }
            }
        }
        return false;
    }

    private static boolean fixTextComponentList(CompoundTag components, String key) {
        // lore 采用类似逻辑（文本组件列表）
        if (components.contains(key)) {
            Tag loreTag = components.get(key);
            if (loreTag instanceof ListTag loreList) {
                ListTag newLore = new ListTag();
                boolean modified = false;

                for (int i = 0; i < loreList.size(); i++) {
                    Tag element = loreList.get(i);
                    if (element instanceof StringTag strTag) {
                        String text = strTag.value();
                        if (text.startsWith("{") && text.endsWith("}")) {
                            try {
                                CompoundTag parsed = TagParser.parseCompoundFully(text);
                                newLore.add(parsed);
                                modified = true;
                                continue;
                            } catch (Exception e) {
                                MyPetApi.getLogger().warning("Failed to parse lore component: " + e.getMessage());
                            }
                        }
                    }
                    newLore.add(element);
                }

                if (modified) {
                    components.put(key, newLore);
                    return true;
                }
            }
        }
        return false;
    }

    public static CompoundTag convertOldVanillaCompound(CompoundTag oldTag) {
        return convertOldVanillaCompound(oldTag, 1519);
    }

    public static CompoundTag convertOldVanillaCompound(CompoundTag oldTag, int fromDataVersion) {
        Dynamic<Tag> dyn = new Dynamic<>(NbtOps.INSTANCE, oldTag);
        Dynamic<Tag> updatedDyn = DataFixers.getDataFixer().update(References.ITEM_STACK, dyn,
                fromDataVersion, currentDataVersion());
        return (CompoundTag) updatedDyn.getValue();
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    public static Tag compoundToVanillaCompound(TagBase tag) {
        switch (TagType.getTypeById(tag.getTagTypeId())) {
            case Int:
                return IntTag.valueOf(((TagInt) tag).getIntData());
            case Short:
                return ShortTag.valueOf(((TagShort) tag).getShortData());
            case String:
                return StringTag.valueOf(((TagString) tag).getStringData());
            case Byte:
                return ByteTag.valueOf(((TagByte) tag).getByteData());
            case Byte_Array:
                return new ByteArrayTag(((TagByteArray) tag).getByteArrayData());
            case Double:
                return DoubleTag.valueOf(((TagDouble) tag).getDoubleData());
            case Float:
                return FloatTag.valueOf(((TagFloat) tag).getFloatData());
            case Int_Array:
                return new IntArrayTag(((TagIntArray) tag).getIntArrayData());
            case Long_Array:
                return new LongArrayTag(((TagLongArray) tag).getLongArrayData());
            case Long:
                return LongTag.valueOf(((TagLong) tag).getLongData());
            case List:
                TagList TagList = (TagList) tag;
                ListTag tagList = new ListTag();
                for (TagBase tagInList : TagList.getReadOnlyList()) {
                    tagList.add(compoundToVanillaCompound(tagInList));
                }
                return tagList;
            case Compound:
                TagCompound TagCompound = (TagCompound) tag;
                CompoundTag tagCompound = new CompoundTag();
                for (String name : TagCompound.getCompoundData().keySet()) {
                    tagCompound.put(name, compoundToVanillaCompound(TagCompound.getCompoundData().get(name)));
                }
                return tagCompound;
            case End:
                return null;
        }
        throw new IllegalArgumentException("Not a valid tag type");
    }

    public static TagBase vanillaCompoundToCompound(Tag vanillaTag) {
        switch (vanillaTag.getId()) {
            case 1:
                return new TagByte(((ByteTag) vanillaTag).byteValue());
            case 2:
                return new TagShort(((ShortTag) vanillaTag).shortValue());
            case 3:
                return new TagInt(((IntTag) vanillaTag).intValue());
            case 4:
                return new TagLong(((LongTag) vanillaTag).longValue());
            case 5:
                return new TagFloat(((FloatTag) vanillaTag).floatValue());
            case 6:
                return new TagDouble(((DoubleTag) vanillaTag).doubleValue());
            case 7:
                return new TagByteArray(((ByteArrayTag) vanillaTag).getAsByteArray());
            case 8:
                return new TagString(((StringTag) vanillaTag).value());
            case 9:
                ListTag tagList = (ListTag) vanillaTag;
                List<TagBase> compoundList = new ArrayList<>();
                try {
                    List<?> list = (List<?>) TAG_LIST_LIST.get(tagList);
                    normalizeMixedListElements(list, compoundList);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

                return new TagList(compoundList);
            case 10:
                TagCompound compound = new TagCompound();
                CompoundTag tagCompound = ((CompoundTag) vanillaTag);
                Set<String> keys = tagCompound.keySet();
                for (String tagName : keys) {
                    compound.getCompoundData().put(tagName, vanillaCompoundToCompound(tagCompound.get(tagName)));
                }
                return compound;
            case 11:
                return new TagIntArray(((IntArrayTag) vanillaTag).getAsIntArray());
            case 12:
                return new TagLongArray(((LongArrayTag) vanillaTag).getAsLongArray());
        }
        return null;
    }

    /**
     * 转换 Mojang 列表中的元素：当列表以 compound 为主时，
     * 将裸 StringTags 升级为 {@code {text: "..."}} CompoundTags。
     *
     * Mojang 的文本组件编解码器（用于 lore、custom_name、extra 参数
     * 列表等）可能生成混合了 StringTag 元素（纯文本组件）和
     * CompoundTag 元素（带样式的组件）的 ListTag。NBT 列表在
     * 传输时必须同质，因此 knbt 的 TagList 会拒绝混合构建。
     * 将零散字符串包装为 {text: ...} compound 是 Minecraft 文本
     * 组件的规范形式，且能保持原有语义。
     */
    private static void normalizeMixedListElements(List<?> source, List<TagBase> out) {
        boolean hasCompound = false;
        boolean hasString = false;
        for (Object element : source) {
            if (element instanceof CompoundTag) hasCompound = true;
            else if (element instanceof StringTag) hasString = true;
        }
        boolean wrapStringsAsCompounds = hasCompound && hasString;

        for (Object element : source) {
            Tag tag = (Tag) element;
            if (wrapStringsAsCompounds && tag instanceof StringTag stringTag) {
                CompoundTag wrapped = new CompoundTag();
                wrapped.putString("text", stringTag.value());
                out.add(vanillaCompoundToCompound(wrapped));
            } else {
                out.add(vanillaCompoundToCompound(tag));
            }
        }
    }
}
