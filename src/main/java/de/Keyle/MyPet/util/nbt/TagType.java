/*
 * This file is part of KNBT
 *
 * Copyright (C) 2011-2026 Keyle
 * KNBT is licensed under the GNU Lesser General Public License.
 *
 * KNBT is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * KNBT is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.util.nbt;

import java.util.HashMap;
import java.util.Map;

public enum TagType {
    End(0, "TAG_End", TagEnd.class),
    Byte(1, "TAG_Byte", TagByte.class),
    Short(2, "TAG_Short", TagShort.class),
    Int(3, "TAG_Int", TagInt.class),
    Long(4, "TAG_Long", TagLong.class),
    Float(5, "TAG_Float", TagFloat.class),
    Double(6, "TAG_Double", TagDouble.class),
    Byte_Array(7, "TAG_Byte_Array", TagByteArray.class),
    String(8, "TAG_String", TagString.class),
    List(9, "TAG_List", TagList.class),
    Compound(10, "TAG_Compound", TagCompound.class),
    Int_Array(11, "TAG_Int_Array", TagIntArray.class),
    Long_Array(12, "TAG_Long_Array", TagLongArray.class);

    private static final Map<Class<? extends TagBase>, TagType> class2Type = new HashMap<>();
    private static final Map<String, TagType> name2Type = new HashMap<>();
    private static final Map<Integer, TagType> id2Type = new HashMap<>();

    static {
        for (TagType type : TagType.values()) {
            class2Type.put(type.getTypeClass(), type);
            name2Type.put(type.getTypeName(), type);
            id2Type.put(type.getTypeId(), type);
        }
    }

    private final int id;
    private final String typeName;
    private final Class<? extends TagBase> typeClass;

    TagType(int id, String typeName, Class<? extends TagBase> typeClass) {
        this.id = id;
        this.typeName = typeName;
        this.typeClass = typeClass;
    }

    public int getTypeId() {
        return id;
    }

    public Class<? extends TagBase> getTypeClass() {
        return typeClass;
    }

    public String getTypeName() {
        return typeName;
    }

    public TagBase createNewTag() {
        try {
            return typeClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T extends TagBase> T createNewTag(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static TagType getTypeByName(String name) {
        return name2Type.get(name);
    }

    public static TagType getTypeById(int id) {
        return id2Type.get(id);
    }

    public static TagType getTypeByClass(Class<? extends TagBase> clazz) {
        return class2Type.get(clazz);
    }
}