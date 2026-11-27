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

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TagStream {
    public static TagCompound readTag(InputStream inputStream, boolean comressed) throws IOException {
        if (comressed) {
            inputStream = new GZIPInputStream(inputStream);
        }
        try (DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(inputStream))) {
            return readRoot(dataInputStream);
        }
    }

    public static void writeTag(TagCompound tagCompound, OutputStream outputStream, boolean comressed) throws IOException {
        if (comressed) {
            outputStream = new GZIPOutputStream(outputStream);
        }
        try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeRoot(tagCompound, dataOutputStream);
        }
    }

    public static TagCompound readTag(byte[] bytes, boolean compressed) throws IOException {
        InputStream inputStream = new ByteArrayInputStream(bytes);
        return readTag(inputStream, compressed);
    }

    public static byte[] writeTag(TagCompound tagCompound, boolean compressed) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        writeTag(tagCompound, (OutputStream) byteArrayOutputStream, compressed);
        return byteArrayOutputStream.toByteArray();
    }

    public static TagCompound readRoot(DataInput dataInput) throws IOException {
        // 从根标签读取数据
        TagBase tagBase = readTags(dataInput);
        if ((tagBase instanceof TagCompound)) {
            return (TagCompound) tagBase;
        }
        throw new IOException("Root tag must be a TagCompound");
    }

    private static void writeRoot(TagCompound tagCompound, DataOutput dataOutput) throws IOException {
        // 将数据写入根标签
        dataOutput.writeByte(TagType.Compound.getTypeId());
        dataOutput.writeUTF("");
        tagCompound.write(dataOutput);
    }

    private static TagBase readTags(DataInput dataInput) throws IOException {
        byte tagTypeId = dataInput.readByte();
        if (tagTypeId != TagType.Compound.getTypeId()) {
            return new TagEnd();
        }

        dataInput.readUTF();
        TagType type = TagType.getTypeById(tagTypeId);

        TagBase newTag = type.createNewTag();
        newTag.load(dataInput, 0);
        return newTag;
    }
}