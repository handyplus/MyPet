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

class TagEnd extends TagBase {
    @Override
    public Object getData() {
        return null;
    }

    @Override
    protected void load(DataInput dataInput, int depth) throws IOException {
    }

    @Override
    protected void write(DataOutput dataOutput) throws IOException {
    }

    @Override
    protected void print(PrintStream stream, int indentation) {
        stream.println("-- End --");
    }

    @Override
    public TagEnd clone() {
        return new TagEnd();
    }
}