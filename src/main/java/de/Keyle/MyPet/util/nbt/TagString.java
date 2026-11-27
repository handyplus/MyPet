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

public class TagString extends TagBase {
    String data;

    protected TagString() {
    }

    public TagString(String data) {
        this.data = data;
    }

    public String getStringData() {
        return data;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    protected void load(DataInput dataInput, int depth) throws IOException {
        data = dataInput.readUTF();
    }

    @Override
    protected void write(DataOutput dataOutput) throws IOException {
        dataOutput.writeUTF(data);
    }

    @Override
    protected void print(PrintStream stream, int indentation) {
        stream.println(getPrintIndentation(indentation) + "S: \"" + data + "\"");
    }

    @Override
    public TagString clone() {
        return new TagString(data);
    }

    public int hashCode() {
        return super.hashCode() ^ data.hashCode();
    }
}