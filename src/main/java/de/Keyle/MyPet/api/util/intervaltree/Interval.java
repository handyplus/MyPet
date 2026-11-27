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

import java.util.Comparator;

/**
 * 通用区间的表示。区间可以是开区间或闭区间（起点和终点可以包含或排除），
 * 也可以是有界或无界的（可以延伸到正无穷或负无穷）。
 * <p>
 * 该类并不假定区间是数值型的，而是泛化为表示元素的连续子集，
 * 此处“连续”是相对于任意全序函数定义的。例如这些元素可以是 {@link java.util.Date}，
 * 或者基本上任何其元素之间可以相互比较的类型。由于该类要求其泛型变量
 * 实现 {@link Comparable} 接口，{@code Interval} 类内部的所有
 * 比较都通过该接口方法完成。
 * <p>
 * 在为 {@code Interval} 类编写子类时请注意，区间的起点和终点
 * <strong>可以</strong>为 {@code null}。起点的 {@code null} 表示
 * 负无穷，终点的 {@code null} 表示正无穷。在覆盖 {@code Interval} 类中
 * 已有默认实现的方法时尤其要记住这一点，例如 {@link #contains(Comparable)}、
 * {@link #isLeftOf(Comparable)}，特别是 {@link #equals(Object)} 和
 * {@link #hashCode()}。
 *
 * @param <T> 表示区间定义域中单个点的类型。
 */
@SuppressWarnings("ALL")
public abstract class Interval<T extends Comparable<? super T>, S> {

    private S value;
    private T start, end;
    private boolean isStartInclusive, isEndInclusive;

    /**
     * 表示有界区间所有可能类型的枚举。
     */
    public enum Bounded {
        /**
         * 起点和终点都不包含的区间。
         */
        OPEN,

        /**
         * 起点和终点都包含的区间。
         */
        CLOSED,

        /**
         * 起点不包含、终点包含的区间。
         */
        CLOSED_RIGHT,

        /**
         * 起点包含、终点不包含的区间。
         */
        CLOSED_LEFT
    }

    public enum Unbounded {
        /**
         * 延伸到正无穷、以下界（不包含的起点）为界的区间。例如 (5, +inf)
         */
        OPEN_LEFT,

        /**
         * 延伸到正无穷、以下界（包含的起点）为界的区间。例如 [5, +inf)
         */
        CLOSED_LEFT,

        /**
         * 延伸到负无穷、以上界（不包含的终点）为界的区间。例如 (-inf, 5)
         */
        OPEN_RIGHT,

        /**
         * 延伸到负无穷、以上界（包含的终点）为界的区间。例如 (-inf, 5]
         */
        CLOSED_RIGHT
    }

    /**
     * 实例化一个表示定义域中所有点的新区间，
     * 即实例化区间 (-inf, +inf)。
     *
     * @param value
     */
    public Interval(S value) {
        this.value = value;
        isStartInclusive = true;
        isEndInclusive = true;
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
    public Interval(T start, T end, S value, Bounded type) {
        this.value = value;
        this.start = start;
        this.end = end;
        if (type == null) {
            type = Bounded.CLOSED;
        }
        switch (type) {
            case OPEN:
                break;
            case CLOSED:
                isStartInclusive = true;
                isEndInclusive = true;
                break;
            case CLOSED_RIGHT:
                isEndInclusive = true;
                break;
            default:
                isStartInclusive = true;
                break;
        }
    }

    /**
     * 实例化一个新的无界区间——即延伸到正无穷或负无穷的区间。该区间由起点
     * 或终点之一界定，而在另一端无界。
     *
     * @param se    起点或终点的界定值。
     * @param value
     * @param type  描述区间是延伸到正无穷还是负无穷，
     *              以及在界定点处是开还是闭。各种可能性的说明参见 {@link Unbounded
     *              the Unbounded enum}。
     */
    public Interval(T se, S value, Unbounded type) {
        this.value = value;
        if (type == null) {
            type = Unbounded.CLOSED_RIGHT;
        }
        switch (type) {
            case OPEN_LEFT:
                start = se;
                isStartInclusive = false;
                isEndInclusive = true;
                break;
            case CLOSED_LEFT:
                start = se;
                isStartInclusive = true;
                isEndInclusive = true;
                break;
            case OPEN_RIGHT:
                end = se;
                isStartInclusive = true;
                isEndInclusive = false;
                break;
            default:
                end = se;
                isStartInclusive = true;
                isEndInclusive = true;
                break;
        }
    }

    /**
     * 检查当前区间是否不包含任何点。
     *
     * <p>特别地，如果终点小于起点，则该区间被视为空。不过还存在其他
     * 区间为空的情形。例如在 {@link IntegerInterval} 类中，起点和终点
     * 相差 1 的开区间（例如区间 (4, 5)）是空的，因为它不包含任何整数。
     * 然而同一个区间在 {@link DoubleInterval} 类中<strong>不会</strong>
     * 被视为空，因为该区间内存在 Double 数值。
     * </p>
     *
     * @return 如果当前区间为空则返回 {@code true}，否则返回 {@code false}。
     */
    public boolean isEmpty() {
        if (start == null || end == null) {
            return false;
        }
        int compare = start.compareTo(end);
        if (compare > 0) {
            return true;
        }
        if (compare == 0 && (!isEndInclusive || !isStartInclusive)) {
            return true;
        }
        return false;
    }

    /**
     * 用于创建特定 {@code Interval} 子类的新实例。
     * <p>
     * {@code Interval} 类力求避免使用反射。不过在某些场合，该类
     * 仍需要创建 {@code Interval} 类的新实例。为保证它们
     * 具有期望的运行时类型，会调用特定引用对象的 {@link #create(Object)} 方法。
     * </p>
     * <p>
     * 通常，实现该抽象方法时你只需要
     * 调用子类的默认构造器并返回新区间。
     * </p>
     *
     * @param value
     * @return 特定 {@code Interval} 类的新实例。
     */
    protected abstract Interval<T, S> create(S value);

    /**
     * 返回当前区间的中心。如果区间中心存在但无法
     * 确定，则返回区间内的任意一点。该方法仅用于
     * 实例化 {@link IntervalTree} 中新 {@link TreeNode} 的中点，因此
     * 不必返回区间的精确中心，但返回它有助于
     * {@link IntervalTree} 获得略好的性能。
     *
     * @return 当前区间的中心点（如果存在），否则返回 {@code null}。如果
     * 中心存在但无法正确确定，则返回区间内的任意一点。
     */
    public abstract T getMidpoint();

    /**
     * 创建特定 {@code Interval} 子类的新实例。
     *
     * @param start            区间的起点
     * @param isStartInclusive 如果起点包含则为 {@code true}，否则为 false
     * @param end              区间的终点
     * @param isEndInclusive   如果终点包含则为 {@code true}，否则为 false
     * @param value
     * @return 新创建的区间。
     */
    protected Interval<T, S> create(T start, boolean isStartInclusive, T end, boolean isEndInclusive, S value) {
        Interval<T, S> interval = create(value);
        interval.start = start;
        interval.isStartInclusive = isStartInclusive;
        interval.end = end;
        interval.isEndInclusive = isEndInclusive;
        return interval;
    }

    /**
     * 返回区间的起点。
     */
    public T getStart() {
        return start;
    }

    /**
     * 返回区间的终点。
     */
    public T getEnd() {
        return end;
    }

    /**
     * 如果起点属于区间则返回 {@code true}，否则返回 false。
     */
    public boolean isStartInclusive() {
        return isStartInclusive;
    }

    /**
     * 如果终点属于区间则返回 {@code true}，否则返回 false。
     */
    public boolean isEndInclusive() {
        return isEndInclusive;
    }

    public S getValue() {
        return value;
    }

    /**
     * 判断当前区间是否为单个点。
     *
     * @return 如果当前区间表示单个点则返回 {@code true}。
     */
    public boolean isPoint() {
        if (start == null || end == null) {
            return false;
        }
        return start.compareTo(end) == 0 && isStartInclusive && isEndInclusive;
    }

    /**
     * 判断当前区间是否包含查询点。
     *
     * @param query 该点。
     * @return 如果当前区间包含 {@code query} 点则返回 {@code true}，否则返回 false。
     */
    public boolean contains(T query) {
        if (isEmpty() || query == null) {
            return false;
        }

        int startCompare = start == null ? 1 : query.compareTo(start);
        int endCompare = end == null ? -1 : query.compareTo(end);
        if (startCompare > 0 && endCompare < 0) {
            return true;
        }
        return (startCompare == 0 && isStartInclusive) || (endCompare == 0 && isEndInclusive);
    }

    /**
     * 返回表示两个区间交集的新区间。更正式地说，对于返回区间中的
     * 每个点 {@code x}，{@code x} 都同时属于当前区间
     * 和 {@code other} 区间。
     *
     * @param other 另一个区间
     * @return 当前区间与 {@code other} 区间的交集。
     */
    public Interval<T, S> getIntersection(Interval<T, S> other) {
        if (other == null || isEmpty() || other.isEmpty()) {
            return null;
        }
        // 确保起点较小的区间与另一个区间求交。
        // 如有必要则交换两个区间
        if ((other.start == null && start != null) || (start != null && start.compareTo(other.start) > 0)) {
            return other.getIntersection(this);
        }
        if (end != null && other.start != null && (end.compareTo(other.start) < 0 || (end.compareTo(other.start) == 0 && (!isEndInclusive || !other.isStartInclusive)))) {
            return null;
        }

        T newStart, newEnd;
        boolean isNewStartInclusive, isNewEndInclusive;

        // 如果 other.start 为 null，说明我的起点也为 null，因为我们已经确保
        // 调用方对象的起点更小 => 新起点为 null
        if (other.start == null) {
            newStart = null;
            isNewStartInclusive = true;
        } else {
            newStart = other.start;
            if (start != null && other.start.compareTo(start) == 0) {
                isNewStartInclusive = other.isStartInclusive && isStartInclusive;
            } else {
                isNewStartInclusive = other.isStartInclusive;
            }
        }

        if (end == null) {
            newEnd = other.end;
            isNewEndInclusive = other.isEndInclusive;
        } else if (other.end == null) {
            newEnd = end;
            isNewEndInclusive = isEndInclusive;
        } else {
            int compare = end.compareTo(other.end);
            if (compare == 0) {
                newEnd = end;
                isNewEndInclusive = isEndInclusive && other.isEndInclusive;
            } else if (compare < 0) {
                newEnd = end;
                isNewEndInclusive = isEndInclusive;
            } else {
                newEnd = other.end;
                isNewEndInclusive = other.isEndInclusive;
            }
        }
        Interval<T, S> intersection = create(newStart, isNewStartInclusive, newEnd, isNewEndInclusive, other.value);
        return intersection.isEmpty() ? null : intersection;
    }

    /**
     * 检查当前区间是否完整包含另一个区间。更正式地说，
     * 如果 {@code another} 区间中的每个点 {@code x}
     * 也都属于当前区间，该方法返回 {@code true}。
     *
     * @param another 另一个区间。
     * @return 如果 {@code another} 区间被当前区间完整包含则返回 {@code true}，
     * 否则返回 {@code false}。
     */
    public boolean contains(Interval<T, S> another) {
        if (another == null || isEmpty() || another.isEmpty()) {
            return false;
        }
        Interval<T, S> intersection = getIntersection(another);
        return intersection != null && intersection.equals(another);
    }

    /**
     * 检查当前区间是否与另一个区间相交。更正式地说，如果当前区间中
     * 至少有一个点也属于 {@code query} 区间，该方法返回 {@code true}。
     *
     * @param query 正在检查是否与当前区间相交的区间。
     * @return 如果两个区间相交则返回 {@code true}，否则返回 {@code false}。
     */
    public boolean intersects(Interval<T, S> query) {
        if (query == null) {
            return false;
        }
        Interval<T, S> intersection = getIntersection(query);
        return intersection != null;
    }

    /**
     * 该方法检查当前区间是否完全位于某个点的右侧。更正式地说，
     * 如果当前区间中的每个点 {@code x} 都满足不等式
     * {@code x} &gt; {@code point}，则该方法返回 {@code true}。如果参数 {@code inclusive}
     * 设为 {@code false}，那么当区间的起点等于参考点 {@code point} 时，
     * 该方法同样返回 {@code true}。
     *
     * @param point     参考点
     * @param inclusive 如果允许参考点 {@code point} 作为当前区间的起点，
     *                  则为 {@code false}。
     * @return 如果当前区间完全位于 {@code other} 区间的右侧则返回 {@code true}，
     * 否则返回 {@code false}。
     */
    public boolean isRightOf(T point, boolean inclusive) {
        if (point == null || start == null) {
            return false;
        }
        int compare = point.compareTo(start);
        if (compare != 0) {
            return compare < 0;
        }
        return !isStartInclusive() || !inclusive;
    }

    /**
     * 该方法检查当前区间是否完全位于某个点的右侧。更正式地说，
     * 如果当前区间中的每个点 {@code x} 都满足不等式
     * {@code x} &gt; {@code point}，则该方法返回 true。由这一形式定义可知，
     * 当当前区间的起点等于参考点 {@code point} 且终点为开时，
     * 该方法将返回 {@code true}。
     *
     * @param point 参考点
     * @return 如果当前区间完全位于 {@code other} 区间的右侧则返回 {@code true}，
     * 否则返回 {@code false}。
     */
    public boolean isRightOf(T point) {
        return isRightOf(point, true);
    }

    /**
     * 该方法检查当前区间是否完全位于另一个区间右侧且两者没有公共点。
     * 更正式地说，如果当前区间中的每个点 {@code x} 与 {@code other} 区间中的
     * 每个点 {@code y} 都满足不等式 {@code x} &gt; {@code y}，则该方法返回 true。
     * 由这一形式定义可知，当当前区间的起点等于 {@code other} 区间的终点时，
     * 仅当两点都包含时该方法才返回 {@code false}，其他情况下均返回 {@code true}。
     *
     * @param other 参考区间
     * @return 如果当前区间完全位于 {@code other} 区间的右侧则返回 {@code true}，
     * 否则返回 {@code false}。
     */
    public boolean isRightOf(Interval<T, S> other) {
        if (other == null || other.isEmpty()) {
            return false;
        }
        return isRightOf(other.end, other.isEndInclusive());
    }

    /**
     * 该方法检查当前区间是否完全位于某个点的左侧。更正式地说，
     * 如果当前区间中的每个点 {@code x} 都满足不等式
     * {@code x} &lt; {@code point}，则该方法返回 {@code true}。如果参数 {@code inclusive}
     * 设为 {@code false}，那么当区间的终点等于参考点 {@code point} 时，
     * 该方法同样返回 {@code true}。
     *
     * @param point     参考点
     * @param inclusive 如果允许参考点 {@code point} 作为当前区间的终点，
     *                  则为 {@code false}。
     * @return 如果当前区间完全位于 {@code other} 区间的左侧则返回 {@code true}，
     * 否则返回 {@code false}。
     */
    public boolean isLeftOf(T point, boolean inclusive) {
        if (point == null || end == null) {
            return false;
        }
        int compare = point.compareTo(end);
        if (compare != 0) {
            return compare > 0;
        }
        return !isEndInclusive() || !inclusive;
    }

    /**
     * 该方法检查当前区间是否完全位于某个点的左侧。更正式地说，
     * 如果当前区间中的每个点 {@code x} 都满足不等式
     * {@code x} &lt; {@code point}，则该方法返回 true。由这一形式定义可知，
     * 当当前区间的终点等于参考点 {@code point} 且终点为开时，
     * 该方法将返回 {@code true}。
     *
     * @param point 参考点
     * @return 如果当前区间完全位于 {@code other} 区间的左侧则返回 {@code true}，
     * 否则返回 {@code false}。
     */
    public boolean isLeftOf(T point) {
        return isLeftOf(point, true);
    }

    /**
     * 该方法检查当前区间是否完全位于另一个区间左侧且两者没有公共点。
     * 更正式地说，如果当前区间中的每个点 {@code x} 与 {@code other} 区间中的
     * 每个点 {@code y} 都满足不等式 {@code x} &lt; {@code y}，则该方法返回 true。
     * 由这一形式定义可知，当当前区间的终点等于 {@code other} 区间的起点时，
     * 仅当两点都包含时该方法才返回 {@code false}，其他情况下均返回 {@code true}。
     *
     * @param other 参考区间
     * @return 如果当前区间完全位于 {@code other} 区间的左侧则返回 {@code true}，
     * 否则返回 {@code false}。
     */
    public boolean isLeftOf(Interval<T, S> other) {
        if (other == null || other.isEmpty()) {
            return false;
        }
        return isLeftOf(other.start, other.isStartInclusive());
    }

    /**
     * 仅考虑区间起点的 {@link Comparator}。它不能也不得
     * 作为独立的 {@link Comparator} 使用，只用于编写更可读、
     * 更模块化的代码。
     */
    private int compareStarts(Interval<T, S> other) {
        if (start == null && other.start == null) {
            return 0;
        }
        if (start == null) {
            return -1;
        }
        if (other.start == null) {
            return 1;
        }
        int compare = start.compareTo(other.start);
        if (compare != 0) {
            return compare;
        }
        if (isStartInclusive ^ other.isStartInclusive) {
            return isStartInclusive ? -1 : 1;
        }
        return 0;
    }

    /**
     * 仅考虑区间终点的 {@link Comparator}。它不能也不得
     * 作为独立的 {@link Comparator} 使用，只用于编写更可读、
     * 更模块化的代码。
     */
    private int compareEnds(Interval<T, S> other) {
        if (end == null && other.end == null) {
            return 0;
        }
        if (end == null) {
            return 1;
        }
        if (other.end == null) {
            return -1;
        }
        int compare = end.compareTo(other.end);
        if (compare != 0) {
            return compare;
        }
        if (isEndInclusive ^ other.isEndInclusive) {
            return isEndInclusive ? 1 : -1;
        }
        return 0;
    }

    /**
     * 可作为排序函数参数使用的比较器。起点比较器以<em>升序</em>排序区间，即把起点
     * 较小的区间排在起点较大的区间之前。这相当于从左到右的扫描线。
     * <p>
     * 起点为 null（负无穷）的区间被视为比其他所有区间都小。
     * 如果两个区间起点相同，则把包含的起点视为小于不包含的起点。
     * 例如 [0, 2) 被视为小于 (0, 2)。
     * </p>
     * <p>
     * 为确保该比较器也能用于集合，当起点相同时它会比较区间的终点。
     * 否则集合将无法处理起点相同的两个不同区间，并会忽略其中一个区间。
     * </p>
     * <p>
     * 由于这是泛型类的静态方法，它会涉及对类方法的非受检调用。用户需自行
     * 确保比较的是同一类的区间，否则可能抛出异常。
     * </p>
     */
    public static Comparator<Interval> sweepLeftToRight = (a, b) -> {
        int compare = a.compareStarts(b);
        if (compare != 0) {
            return compare;
        }
        compare = a.compareEnds(b);
        if (compare != 0) {
            return compare;
        }
        return a.compareSpecialization(b);
    };

    /**
     * 可作为排序函数参数使用的比较器。终点比较器以<em>降序</em>排序区间，即把终点
     * 较大的区间排在终点较小的区间之前。这相当于从右到左的扫描线。
     * <p>
     * 终点为 null（正无穷）的区间排在其他所有区间之前。如果两个区间
     * 终点相同，则包含的终点排在不包含的终点之前。例如 [0, 10) 排在
     * (0, 10] 之后。
     * </p>
     * <p>
     * 为确保该比较器也能用于集合，当终点相同时它会比较区间的起点。
     * 否则集合将无法处理终点相同的两个不同区间，并会忽略其中一个区间。
     * </p>
     * <p>
     * 由于这是泛型类的静态方法，它会涉及对类方法的非受检调用。用户需自行
     * 确保比较的是同一类的区间，否则可能抛出异常。
     * </p>
     */
    public static Comparator<Interval> sweepRightToLeft = (a, b) -> {
        int compare = b.compareEnds(a);
        if (compare != 0) {
            return compare;
        }
        compare = b.compareStarts(a);
        if (compare != 0) {
            return compare;
        }
        return a.compareSpecialization(b);
    };

    /**
     * 如果 {@code Interval} 的子类具有刻画该类对象并用于标识它们的属性，
     * 就应覆盖此方法。它用于在不同对象之间建立全序，否则若只考虑
     * 起点和终点，这些对象会被视为相等。如果你没有这类特殊属性，
     * 可以保留该方法的默认实现。
     * <p>
     * 该方法的作用类似于传统的 {@link Comparator}，但不能也不应单独使用，
     * 也不应把它实现为完整的独立比较器。它总是与 {@code Interval} 类中的
     * 两个基础 {@link Comparator} 之一——{@link #sweepLeftToRight} 和
     * {@link #sweepRightToLeft}——配合使用。只有当主比较器返回 0（即它认为
     * 两个区间相等）时，该方法才会执行。此时起点和终点已经被比较过，
     * 因此该方法应<strong>完全忽略起点和终点</strong>，只考虑
     * 特定子类中定义的特殊属性。
     * </p>
     * <p>
     * 如果子类中有任何用于标识区间的属性（例如用户 ID、学号或房间号），
     * 覆盖此方法至关重要。两个基础比较器会在底层的 {@link java.util.TreeSet} 中使用，
     * 而 TreeSet 可能会丢弃起点和终点相同的两个不同区间对象。
     * </p>
     *
     * @param other 与此区间进行比较的对象
     * @return <ul>
     * <li>-1，如果此对象小于 {@code other}，</li>
     * <li>0，如果两个对象相等，</li>
     * <li>1，如果此对象大于 {@code other}。</li>
     * </ul>
     */
    protected int compareSpecialization(Interval<T, S> other) {
        return 0;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = start == null ? 0 : start.hashCode();
        result = prime * result + (end == null ? 0 : end.hashCode());
        result = prime * result + (isStartInclusive ? 1 : 0);
        result = prime * result + (isEndInclusive ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Interval)) {
            return false;
        }
        Interval<T, S> other = (Interval<T, S>) obj;
        if (start == null ^ other.start == null) {
            return false;
        }
        if (value == null ^ other.value == null) {
            return false;
        }
        if (!value.equals(other.value)) {
            return false;
        }
        if (end == null ^ other.end == null) {
            return false;
        }
        if (isEndInclusive ^ other.isEndInclusive) {
            return false;
        }
        if (isStartInclusive ^ other.isStartInclusive) {
            return false;
        }
        if (start != null && !start.equals(other.start)) {
            return false;
        }
        if (end != null && !end.equals(other.end)) {
            return false;
        }
        return true;
    }


    public Builder builder() {
        return new Builder(this, value);
    }

    /**
     * 用于通过建造者模式直观地创建新区间。
     * 由于 {@code Interval} 类的实现力求避免反射，尽管它是泛型类，
     * {@code Builder} 内部类也不是静态的。相反，它总是绑定到
     * 特定对象，从而能基于该具体的运行时类型构建新对象。
     */
    public class Builder {

        private Interval<T, S> interval;

        /**
         * {@code private} 构造器，仅在 {@link Interval} 类内部使用。
         * 你可以通过已有对象创建该类的新实例：
         * <pre>existingInterval.builder()</pre>
         * 也可以用默认构造器实例化一个“全集”区间，再调用它的
         * {@link Interval#builder(Object) builder} 方法：
         * <pre>new IntegerInterval().builder()</pre>
         *
         * @param ref   仅用于确定新对象运行时类型的引用对象。引用区间
         *              不会以任何方式影响新区间的起点和终点。
         * @param value
         */
        private Builder(Interval<T, S> ref, S value) {
            interval = ref.create(value);
        }

        /**
         * 将正在构建的区间的起点设为给定值。该区间左端为开。
         * 如果多次调用此方法，或与 {@link #greaterEqual(Comparable)} 方法
         * 一并调用，则只有后续调用中的最后一次生效。
         *
         * @param start 新区间起点的取值。
         */
        public Builder greater(T start) {
            interval.start = start;
            interval.isStartInclusive = false;
            return this;
        }

        /**
         * 将正在构建的区间的起点设为给定值。该区间左端为闭。
         * 如果多次调用此方法，或与 {@link #greater(Comparable)} 方法
         * 一并调用，则只有后续调用中的最后一次生效。
         *
         * @param start 新区间起点的取值。
         */
        public Builder greaterEqual(T start) {
            interval.start = start;
            interval.isStartInclusive = true;
            return this;
        }

        /**
         * 将正在构建的区间的终点设为给定值。该区间右端为开。
         * 如果多次调用此方法，或与 {@link #lessEqual(Comparable)} 方法
         * 一并调用，则只有后续调用中的最后一次生效。
         *
         * @param end 新区间终点的取值。
         */
        public Builder less(T end) {
            interval.end = end;
            interval.isEndInclusive = false;
            return this;
        }

        /**
         * 将正在构建的区间的终点设为给定值。该区间右端为闭。
         * 如果多次调用此方法，或与 {@link #lessEqual(Comparable)} 方法
         * 一并调用，则只有后续调用中的最后一次生效。
         *
         * @param end 新区间终点的取值。
         */
        public Builder lessEqual(T end) {
            interval.end = end;
            interval.isEndInclusive = true;
            return this;
        }

        /**
         * 构建新区间
         *
         * @return 新创建的区间。
         */
        public Interval<T, S> build() {
            return interval;
        }
    }
}
