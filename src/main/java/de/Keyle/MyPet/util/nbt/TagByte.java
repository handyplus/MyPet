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

public class TagByte extends TagBase {

    byte data;

    protected TagByte() {
    }

    public TagByte(byte data) {
        this.data = data;
    }

    public TagByte(int data) {
        this.data = (byte) data;
    }

    public TagByte(boolean data) {
        this.data = (byte) (data ? 1 : 0);
    }

    public byte getByteData() {
        return data;
    }

    public boolean getBooleanData() {
        return data != 0;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    protected void load(DataInput dataInput, int depth) throws IOException {
        this.data = dataInput.readByte();
    }

    @Override
    protected void write(DataOutput dataOutput) throws IOException {
        dataOutput.writeByte(data);
    }

    @Override
    protected void print(PrintStream stream, int indentation) {
        stream.println(getPrintIndentation(indentation) + "b: " + data);
    }

    @Override
    public TagByte clone() {
        return new TagByte(data);
    }

    public int hashCode() {
        return super.hashCode() ^ data;
    }

    @Override
    public boolean equals(Object o, boolean strict) {
        boolean result = super.equals(o, strict);
        if (!result || strict) {
            return result;
        }
        TagBase base = (TagBase) o;
        int valA = getByteData();
        double valB;

        switch (base.getTagType()) {
            case Byte:
                valB = ((TagByte) base).getByteData();
                break;
            case Int:
                valB = ((TagInt) base).getIntData();
                break;
            case Double:
                valB = (int) ((TagDouble) base).getDoubleData();
                break;
            case Float:
                valB = ((TagFloat) base).getFloatData();
                break;
            case String:
                try {
                    valB = Double.parseDouble(((TagString) base).getStringData());
                } catch (Throwable e) {
                    return false;
                }
                break;
            default:
                return false;
        }
        return valA == valB;
    }
}