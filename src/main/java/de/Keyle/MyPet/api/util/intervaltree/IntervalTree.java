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

import java.util.*;

/**
 * 居中间隔树（Centered Interval Tree）的实现，用于在区间集合中高效查找。参见
 * <a href="https://en.wikipedia.org/wiki/Interval_tree">https://en.wikipedia.org/wiki/Interval_tree</a>。
 *
 * <p>
 * 该树作为集合使用，这意味着它不会重复存储同一个区间。更正式地说，树中任意两个
 * 不同的区间 x 和 y，都能保证 x.equals(y) 为 false。如果你尝试向树中添加一个
 * 已经存在的区间，树会拒绝它。更多信息参见
 * {@link #add(Interval) the add method} 的文档。该树也<strong>不</strong>接受
 * {@code null} 或空区间，即 {@link Interval#isEmpty()} 方法计算结果为 {@code true} 的区间。
 * </p>
 * <p>
 * 树的 {@link #iterator()} 方法返回快速失败（fail-fast）迭代器，如果在迭代过程中
 * 以任何方式修改了树（使用迭代器自身的 {@code remove} 方法除外），它将抛出
 * {@link ConcurrentModificationException}。不过这只是尽力而为的做法，因为在
 * 使用非原子且未同步的方法时通常很难保证这一行为。
 * </p>
 * <p>
 * 该树依赖 {@link Interval} 类的子类来表示区间。大部分区间方法已在
 * {@code Interval} 类中实现，扩展类无需再实现。区间之间的比较
 * 也已在 {@code Interval} 类中预实现，它使用起点和终点为所有存储的区间建立
 * 全序。不过，如果树需要存储起点和终点相同、但代表不同
 * 逻辑实体的区间，就需要一个覆盖 {@code equals}、{@code hashCode}
 * 和 {@code compareTo} 方法的子类。更多信息参见 {@link Interval} 的文档。
 * </p>
 *
 * @param <T> 区间起点和终点的类型
 */
public class IntervalTree<T extends Comparable<? super T>, S> extends AbstractSet<Interval<T, S>> {

    /**
     * 当前间隔树的根节点。树为空时初始为 {@code null}，并且在向树中添加
     * 或移除区间时可能发生变化。
     */
    TreeNode<T, S> root;

    /**
     * 间隔树的大小，即其中存储的区间数量。
     */
    int size;

    /**
     * 向树中添加一个区间。如果区间为空，则会被拒绝，不会
     * 存储到树中。该操作可能触发树的再平衡，进而
     * 导致区间被 {@link TreeNode#assimilateOverlappingIntervals(TreeNode) assimilated}。
     * 因此该操作在最坏情况下可能耗时 {@code O(n)}，尽管
     * 由于二叉树的性质，平均情况下应为 {@code O(logn)}。
     *
     * @param interval 要添加到树中的区间。
     * @return 如果树因该操作而被修改则返回 {@code true}，
     * 否则返回 {@code false}。
     */
    @Override
    public boolean add(Interval<T, S> interval) {
        if (interval.isEmpty()) {
            return false;
        }
        int sizeBeforeOperation = size;
        root = TreeNode.addInterval(this, root, interval);
        return size == sizeBeforeOperation;
    }

    /**
     * 查找并返回树中存储的所有包含给定查询点的区间。在遍历每个
     * 已访问节点中存储的区间的时间复杂度为摊还 {@code O(1)} 的前提下，该操作
     * 保证耗时 {@code O(logn + k)}，其中 {@code n} 为树的大小、{@code k} 为返回集合的大小。
     * 当前 {@link TreeNode} 的实现使用 {@link TreeSet}，满足这一前提。
     *
     * @param point 查询点。
     * @return 包含树中所有与查询点相交的区间的集合。
     */
    public Set<Interval<T, S>> query(T point) {
        return TreeNode.query(root, point, new HashSet<>());
    }

    /**
     * 查找并返回树中存储的所有与给定查询区间相交的区间。在遍历每个
     * 已访问节点中存储的区间的时间复杂度为摊还 {@code O(1)} 的前提下，该操作
     * 保证耗时 {@code O(logn + k)}，其中 {@code n} 为树的大小、{@code k} 为返回集合的大小。
     * 当前 {@link TreeNode} 的实现使用 {@link TreeSet}，满足这一前提。
     *
     * @param interval 查询区间。
     * @return 包含树中所有与查询区间相交的区间的集合。
     */
    public Set<Interval<T, S>> query(Interval<T, S> interval) {
        Set<Interval<T, S>> result = new HashSet<>();

        if (root == null || interval.isEmpty()) {
            return result;
        }
        TreeNode<T, S> node = root;
        while (node != null) {
            if (interval.contains(node.midpoint)) {
                result.addAll(node.increasing);
                TreeNode.rangeQueryLeft(node.left, interval, result);
                TreeNode.rangeQueryRight(node.right, interval, result);
                break;
            }
            if (interval.isLeftOf(node.midpoint)) {
                for (Interval<T, S> next : node.increasing) {
                    if (!interval.intersects(next)) {
                        break;
                    }
                    result.add(next);
                }
                node = node.left;
            } else {
                for (Interval<T, S> next : node.decreasing) {
                    if (!interval.intersects(next)) {
                        break;
                    }
                    result.add(next);
                }
                node = node.right;
            }
        }
        return result;
    }

    /**
     * 如果树中存储了指定区间，则将其从树中移除。该操作可能触发
     * {@link TreeNode#deleteNode(TreeNode) deletion of a node}，进而
     * 导致树的再平衡，以及区间从一个节点
     * {@link TreeNode#assimilateOverlappingIntervals(TreeNode) assimilation} 到另一个节点。
     * 因此该操作在最坏情况下可能耗时 {@code O(n)}，尽管
     * 由于二叉树的性质，平均情况下应为 {@code O(logn)}。
     *
     * @param interval
     * @return
     */
    public boolean remove(Interval<T, S> interval) {
        if (interval.isEmpty() || root == null) {
            return false;
        }
        int sizeBeforeOperation = size;
        root = TreeNode.removeInterval(this, root, interval);
        return size == sizeBeforeOperation;
    }


    // =========================================================================
    // ============== 树中区间的迭代器 ==================
    // =========================================================================

    @Override
    public Iterator<Interval<T, S>> iterator() {
        if (root == null) {
            return Collections.emptyIterator();
        } else {
            final TreeNode.TreeNodeIterator it = root.iterator();
            return new Iterator<Interval<T, S>>() {
                @Override
                public void remove() {
                    if (it.currentNode.increasing.size() == 1) {
                        root = TreeNode.removeInterval(IntervalTree.this, root, it.currentInterval);

                        // 重建迭代器中的整个分支栈，因为我们可能
                        // 移动了节点并在分支中引入了新节点。规则是：
                        // 将所有节点都加入分支栈，条件是当前节点是
                        // 它们的左后代。
                        TreeNode<T, S> node = root;
                        it.stack = new Stack<>();

                        // 根据上述规则继续压入元素，直到
                        // 到达 subtreeRoot——它是迭代器接下来
                        // 标记要遍历的子树的根。该子树不得
                        // 成为分支栈的一部分，否则某些区间会被
                        // 迭代两次。
                        while (node != it.subtreeRoot) {
                            if (it.currentNode.midpoint.compareTo(node.midpoint) < 0) {
                                it.stack.push(node);
                                node = node.left;
                            } else {
                                node = node.right;
                            }
                        }
                    } else {
                        it.remove();
                    }
                }

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Interval<T, S> next() {
                    return it.next();
                }
            };
        }
    }


    // =========================================================================
    // ================== 来自 Set 接口的方法 =======================
    // =========================================================================

    /**
     * 返回树的大小。
     *
     * @return 树中存储的区间数量。
     */
    public int size() {
        return size;
    }

    /**
     * 移除树中的所有区间。这是最坏情况耗时 {@code O(1)}
     * 的操作。
     */
    @Override
    public void clear() {
        size = 0;
        root = null;
    }

    /**
     * 检查给定对象是否存储在树中。该方法使用二分
     * 查找而非遍历所有区间，因此保证最坏情况耗时
     * {@code O(logn)}。
     *
     * @param o 查询对象。
     * @return 如果对象存储在树中则返回 {@code true}，否则
     * 返回 {@code false}。
     */
    @Override
    public boolean contains(Object o) {
        if (root == null || o == null) {
            return false;
        }
        if (!(o instanceof Interval)) {
            return false;
        }
        Interval<T, S> query;
        query = (Interval<T, S>) o;
        TreeNode<T, S> node = root;
        while (node != null) {
            if (query.contains(node.midpoint)) {
                return node.increasing.contains(query);
            }
            if (query.isLeftOf(node.midpoint)) {
                node = node.left;
            } else {
                node = node.right;
            }
        }

        return false;
    }
}