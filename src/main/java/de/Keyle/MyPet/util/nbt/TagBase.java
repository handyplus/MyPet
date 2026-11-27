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

public abstract class TagBase implements Cloneable {

    public abstract Object getData();

    public abstract TagBase clone();

    protected abstract void load(DataInput dataInput, int depth) throws IOException;

    protected abstract void write(DataOutput dataOutput) throws IOException;

    protected abstract void print(PrintStream stream, int indentation);

    public void print() {
        this.print(System.out, 0);
    }

    protected String getPrintIndentation(int indentation) {
        return new String(new char[indentation]).replace("\0", " ");
    }

    public void print(PrintStream stream) {
        if (stream == null) {
            stream = System.out;
        }
        this.print(stream, 0);
    }

    public int getTagTypeId() {
        return getTagType().getTypeId();
    }

    public TagType getTagType() {
        return TagType.getTypeByClass(this.getClass());
    }

    @Override
    public String toString() {
        return getTagType().name() + " -> " + getData().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TagBase)) {
            return false;
        }

        TagBase tagBase = (TagBase) o;

        if (tagBase.getTagTypeId() != this.getTagTypeId()) {
            return false;
        }

        return getData().equals(tagBase.getData());

    }

    public boolean equals(Object o, boolean strict) {
        if (strict) {
            return this.equals(o);
        }
        return o instanceof TagBase;

    }

    @Override
    public int hashCode() {
        return TagType.getTypeByClass(this.getClass()).getTypeId();
    }
}