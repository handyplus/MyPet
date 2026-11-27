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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TagList extends TagBase {

    List<TagBase> data;
    TagType type;

    public TagList() {
        this.data = new ArrayList<>();
    }

    public TagList(List<? extends TagBase> data) {
        this.data = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return;
        }
        int expectedTypeId = data.get(0).getTagTypeId();
        for (TagBase element : data) {
            if (element.getTagTypeId() != expectedTypeId) {
                throw new IllegalArgumentException(
                        "TagList must be homogeneous: expected all elements to be "
                                + TagType.getTypeById(expectedTypeId).getTypeName()
                                + " but found " + TagType.getTypeById(element.getTagTypeId()).getTypeName());
            }
        }
        this.data.addAll(data);
    }

    public TagType getType() {
        if (data.isEmpty()) {
            return TagType.End;
        }
        return TagType.getTypeByClass(data.get(0).getClass());
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public int size() {
        return data.size();
    }

    public void addTag(TagBase tag) {
        if (!data.isEmpty()) {
            if (tag.getTagTypeId() != getType().getTypeId()) {
                return;
            }
        }
        data.add(tag);
    }

    public <T extends TagBase> T getTagAs(int index, Class<? extends T> clazz) {
        return clazz.cast(data.get(index));
    }

    @SuppressWarnings("unchecked")
    public <T extends TagBase> T getTag(int index) {
        return (T) data.get(index);
    }

    public void removeTag(int index) {
        data.remove(index);
    }

    public void removeTag(TagBase tag) {
        data.remove(tag);
    }

    public List<TagBase> getReadOnlyList() {
        return Collections.unmodifiableList(data);
    }

    public <T extends TagBase> List<T> getListAs(Class<? extends T> clazz) {
        List<T> newList = new ArrayList<>();
        for (TagBase tag : data) {
            if (clazz.isInstance(tag)) {
                newList.add(clazz.cast(tag));
            }
        }
        return newList;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    protected void load(DataInput dataInput, int depth) throws IOException {
        type = TagType.getTypeById(dataInput.readByte());
        int length = dataInput.readInt();
        for (int i = 0; i < length; i++) {
            TagBase newTag = type.createNewTag();
            if (newTag != null) {
                newTag.load(dataInput, depth + 1);
                data.add(newTag);
            }
        }
    }

    @Override
    protected void write(DataOutput dataOutput) throws IOException {
        TagType listType = getType();
        int expectedTypeId = listType.getTypeId();
        for (TagBase tag : data) {
            if (tag.getTagTypeId() != expectedTypeId) {
                throw new IOException(
                        "Refusing to serialize heterogeneous TagList: declared element type is "
                                + listType.getTypeName()
                                + " but found " + TagType.getTypeById(tag.getTagTypeId()).getTypeName()
                                + ". NBT lists are homogeneous by specification.");
            }
        }
        dataOutput.writeByte(expectedTypeId);
        dataOutput.writeInt(data.size());
        for (TagBase tag : data) {
            tag.write(dataOutput);
        }
    }

    @Override
    protected void print(PrintStream stream, int indentation) {
        stream.println(getPrintIndentation(indentation) + "L: size -> " + data.size() + ", type -> " + getType().name());
        if (!data.isEmpty()) {
            stream.println(getPrintIndentation(indentation + 2) + "[");
        }
        for (TagBase tag : data) {
            tag.print(stream, indentation + 2);
        }
        if (!data.isEmpty()) {
            stream.println(getPrintIndentation(indentation + 2) + "]");
        }
    }

    @Override
    public String toString() {
        return getTagType().name() + " -> size: " + data.size();
    }

    @Override
    public TagList clone() {
        List<TagBase> newData = new ArrayList<>();
        for (TagBase tag : data) {
            newData.add(tag.clone());
        }
        return new TagList(newData);
    }

    public int hashCode() {
        return super.hashCode() ^ data.hashCode();
    }
}