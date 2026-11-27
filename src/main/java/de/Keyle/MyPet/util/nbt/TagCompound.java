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
import java.util.HashMap;
import java.util.Map;

public class TagCompound extends TagBase {
    Map<String, TagBase> data;

    public TagCompound() {
        data = new HashMap<>();
    }

    public TagCompound(Map<String, TagBase> data) {
        this.data = new HashMap<>();
        if (data != null) {
            this.data.putAll(data);
        }
    }

    public Map<String, TagBase> getCompoundData() {
        return data;
    }

    public <T extends TagBase> T getAs(String key, Class<T> clazz) {
        if (containsKeyAs(key, clazz)) {
            return clazz.cast(data.get(key));
        }
        return null;
    }

    public void put(String key, TagBase tag) {
        data.put(key, tag);
    }

    public void remove(String key) {
        data.remove(key);
    }

    @SuppressWarnings("unchecked")
    public <T extends TagBase> T get(String key) {
        return (T) data.get(key);
    }

    public <T extends TagBase> boolean containsKeyAs(String key, Class<T> clazz) {
        return containsKey(key) && data.get(key).getClass().equals(clazz);
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public TagCompound clone() {
        Map<String, TagBase> newData = new HashMap<>();
        for (String key : data.keySet()) {
            newData.put(key, data.get(key).clone());
        }
        return new TagCompound(newData);
    }

    @Override
    protected void load(DataInput dataInput, int depth) throws IOException {
        data.clear();
        byte tagTypeId;
        while ((tagTypeId = dataInput.readByte()) != 0) {
            String key = dataInput.readUTF();
            TagType type = TagType.getTypeById(tagTypeId);
            TagBase newTag = type.createNewTag();
            newTag.load(dataInput, depth + 1);
            data.put(key, newTag);
        }
    }

    private static void writeKey(String key, TagBase tag, DataOutput dataOutput) throws IOException {
        dataOutput.writeByte(tag.getTagTypeId());
        if (tag.getTagTypeId() == 0) {
            return;
        }
        dataOutput.writeUTF(key);
        tag.write(dataOutput);
    }

    @Override
    protected void write(DataOutput dataOutput) throws IOException {
        for (String str : this.data.keySet()) {
            TagBase tag = this.data.get(str);
            writeKey(str, tag, dataOutput);
        }
        dataOutput.writeByte(0);
    }

    @Override
    protected void print(PrintStream stream, int indentation) {
        stream.println(getPrintIndentation(indentation) + "C: size -> " + data.size());
        for (String key : data.keySet()) {
            stream.print(getPrintIndentation(indentation + 2) + key + " -> ");
            data.get(key).print(stream, indentation + 4);
        }
    }

    @Override
    public String toString() {
        return getTagType().name() + " -> size: " + data.size() + ", keyset: " + data.keySet();
    }

    public int hashCode() {
        return super.hashCode() ^ data.hashCode();
    }
}