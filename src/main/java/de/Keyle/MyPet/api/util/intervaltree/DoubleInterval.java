/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2019 Keyle
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

package de.Keyle.MyPet.api.util.intervaltree;

/**
 * 以 Double 作为起点和终点的区间。
 * <p>
 * 本类不区分 -0.0 与 0.0，将二者视为同一个值；也就是说，equals 方法会忽略这两个
 * Double 之间的差异。
 * </p>
 * <p>
 * 如果区间的起点或终点为 Double.NaN，则该区间视为空区间。
 * </p>
 */
public class DoubleInterval<S> extends Interval<Double, S> {

    private static final int OFFSET = 1_000;

    /**
     * 实例化一个从正无穷延伸到负无穷、因而包含所有 Double 的区间。
     */
    public DoubleInterval(S value) {
        super(value);
    }

    /**
     * 实例化一个新的有界区间。
     *
     * @param start 区间的起点
     * @param end   区间的终点。
     * @param value
     * @param type  描述区间在一端或两端是开还是闭。
     *              参见 {@link Bounded the documentation of the Bounded enum}
     */
    public DoubleInterval(Double start, Double end, S value, Bounded type) {
        super(0.0 + start, 0.0 + end, value, type);
    }

    /**
     * 实例化一个新的无界区间——即延伸到正无穷或负无穷的区间。该区间由起点
     * 或终点之一界定，而在另一端无界。
     *
     * @param se    起点或终点的界定值。
     * @param value 起点或终点的界定值。
     * @param type  描述区间是延伸到正无穷还是负无穷，
     *              以及在界定点处是开还是闭。各种可能性的说明参见 {@link Unbounded
     *              the Unbounded enum}。
     */
    public DoubleInterval(Double se, S value, Unbounded type) {
        super(0.0 + se, value, type);
    }

    @Override
    protected Interval<Double, S> create(S value) {
        return new DoubleInterval<>(value);
    }

    /**
     * 判断区间是否为空，即不包含任何 Double。与
     * {@link IntegerInterval} 类不同，例如区间 (4.0, 5.0)
     * <strong>不会</strong>被视为空，因为该区间内存在 Double 值。
     * <p>
     * 如果区间的起点或终点为 Double.NaN，则该区间被视为空。
     * </p>
     *
     * @return
     */
    @Override
    public boolean isEmpty() {
        if (getStart() != null && getStart().isNaN()) {
            return true;
        }
        if (getEnd() != null && getEnd().isNaN()) {
            return true;
        }
        if (getStart() != null && getEnd() != null) {
            if (getStart() == Double.POSITIVE_INFINITY && getEnd() == Double.POSITIVE_INFINITY) {
                return true;
            }
            if (getStart() == Double.NEGATIVE_INFINITY && getEnd() == Double.NEGATIVE_INFINITY) {
                return true;
            }
        }
        return super.isEmpty();
    }

    /**
     * 计算区间的中点。
     * <p>
     * 这以一种尽力而为的方式进行，因为在无界区间中并不存在中点。
     * 这与 {@link IntegerInterval} 类不同，后者即使是无界区间
     * 也以 Integer.MAX_VALUE 和/或 Integer.MIN_VALUE 为界。如果 DoubleInterval 是
     * 无界的，该方法会返回一个位于区间内的任意 Double，但不保证
     * 它具体是哪个值。这能确保无界区间
     * 仍能与 {@link TreeNode} 正常工作。当前实现返回一个
     * 与区间界定点相距特定 {@link #OFFSET} 的值。不过这一
     * 行为将来可能改变，因此不要依赖它。
     * </p>
     *
     * @return 区间的中点；如果区间无界，则为区间内的任意一点。
     */
    @Override
    public Double getMidpoint() {
        if (isEmpty()) {
            return null;
        }

        // 处理 null 值
        if (getStart() == null && getEnd() == null) {
            return 0.0;
        }
        if (getStart() == null) {
            return getEnd() - OFFSET;
        }
        if (getEnd() == null) {
            return getStart() + OFFSET;
        }

        // 此时可以确定不再涉及 null 值
        if (getStart() == Double.NEGATIVE_INFINITY && getEnd() == Double.POSITIVE_INFINITY) {
            return 0.0;
        }
        if (getStart() == Double.NEGATIVE_INFINITY) {
            return getEnd() - OFFSET;
        }
        if (getEnd() == Double.POSITIVE_INFINITY) {
            return getStart() + OFFSET;
        }
        return getStart() + (getEnd() - getStart()) / 2;
    }
}
