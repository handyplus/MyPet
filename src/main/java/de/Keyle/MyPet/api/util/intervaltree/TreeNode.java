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
 * {@link IntervalTree} 中单个节点的表示。
 * <p>
 * 由于间隔树实际上是一棵二叉搜索树，每个节点都由唯一的键标识——
 * 即触发创建该节点的第一个区间的中心（称为中点）。该键不可变，
 * 在本节点的生命周期内不能更改。不过与普通平衡搜索树一样，
 * 它可能因平衡旋转而在树中上移或下移。与传统二叉搜索树不同，
 * 间隔树中的每个节点可以存储多个区间。不变量是：每个节点
 * 只存储包含其中点的区间。
 * </p>
 * <p>
 * {@code TreeNode} 需要维护若干不变量。首先，
 * 以每个节点为根的子树始终保持平衡。其次，每个节点
 * 必须至少包含一个区间，否则必须从树中移除该节点，
 * 以使其尽可能小。平衡通过传统的
 * 二叉搜索树旋转完成。
 * </p>
 * <p>
 * 给定一个需要查找、插入或从树中移除的区间，
 * 会以如下方式执行增强版二叉搜索算法。
 * 对每个访问的节点，检查该节点的中点是否包含在
 * 查询区间中。如果是，则该节点就是查询区间
 * 应存储的位置。否则，如果区间完全
 * 位于中点左侧则访问左子节点，反之访问右子节点。
 * </p>
 * <p>
 * 由于区间可能包含树中多个节点的中点，基于上述增强版二叉搜索算法，
 * 一个区间可能有多个有效的存储位置。不过，为确保
 * {@link IntervalTree#add(Interval) add}、
 * {@link IntervalTree#remove(Object) remove} 和
 * {@link IntervalTree#contains(Object) contains} 操作的最佳时间复杂度，
 * 我们将区间的基准位置（或基准，base）定义为：在该区间可能存储的
 * 节点集合中，距离根最近的节点。为保证算法的正确性，
 * 每次树操作后都必须保持这一不变量。这在执行树旋转时
 * 尤为重要。每当某节点因旋转而被提升得更靠近根时，
 * 被提升的节点必须接收（assimilate）因该旋转而降级的节点中的
 * 所有区间。这确保每当二叉搜索算法遇到
 * 一个可作为给定区间有效位置的节点时，该节点就是该区间
 * 的基准。
 * </p>
 * <p>
 * 由于每个节点可以存储多个区间，我们需要一种高效的方式
 * 执行相交查询（给定点或区间形式的查询，找出所有与查询相交的区间）。
 * {@code TreeNode} 不能简单地保存一个含其全部区间的 ArrayList，
 * 因为那样就必须线性检查 List 中的每个区间，这会
 * 降低树的性能，尤其是在单个节点包含所有区间的情况下。
 * 区间必须以有序方式存储。每个节点同时用两个 {@link TreeSet} 保存其区间——
 * 一个按起点升序排列，另一个按终点降序排列。
 * 每当需要给出该节点中所有与查询点相交的区间集合时，
 * 我们检查查询位于该节点中点的左侧还是右侧。
 * 如果在左侧，则按起点排序遍历 TreeSet，直到
 * 遇到完全位于查询右侧的区间。类似地，
 * 如果查询位于中点右侧，则按终点排序遍历集合，
 * 直到遇到完全位于查询左侧的区间。这样我们就只需遍历
 * 真正会作为查询结果返回的区间。
 * </p>
 *
 * @param <T> 区间起点和终点的类型
 */
public class TreeNode<T extends Comparable<? super T>, S> implements Iterable<Interval<T, S>> {

    /**
     * 包含存储于本节点的所有 {@link Interval} 的集合，按其
     * 起点排序。
     *
     * @see Interval#sweepLeftToRight
     */
    protected NavigableSet<Interval<T, S>> increasing;

    /**
     * 包含存储于本节点的所有 {@link Interval} 的集合，按其
     * 终点排序。
     *
     * @see Interval#sweepRightToLeft
     */
    protected NavigableSet<Interval<T, S>> decreasing;

    /**
     * 指向当前节点左子节点的指针。左子节点要么为
     * {@code null}，要么其中点小于当前节点的中点。更
     * 正式地说，{@code left.midpoint.compareTo(this.midpoint) < 0} 必须为 {@code true}。
     */
    protected TreeNode<T, S> left;

    /**
     * 指向当前节点右子节点的指针。右子节点要么为
     * {@code null}，要么其中点大于当前节点的中点。更
     * 正式地说，{@code right.midpoint.compareTo(this.midpoint) > 0} 必须为 {@code true}。
     */
    protected TreeNode<T, S> right;

    /**
     * 添加到该节点的初始区间的中点。它是一个不可变值，
     * 即使初始区间已从该节点中移除也不能更改。
     */
    protected final T midpoint;

    /**
     * 节点的高度。
     */
    protected int height;

    /**
     * 实例化 {@link IntervalTree} 中的一个新节点。
     *
     * @param interval 存储在节点中的初始区间。节点的中点
     *                 将基于该区间设置。
     */
    public TreeNode(Interval<T, S> interval) {
        decreasing = new TreeSet<>(Interval.sweepRightToLeft);
        increasing = new TreeSet<>(Interval.sweepLeftToRight);

        decreasing.add(interval);
        increasing.add(interval);
        midpoint = interval.getMidpoint();
        height = 1;
    }

    /**
     * {@link IntervalTree#add(Interval)} 方法的辅助函数。向以某个 {@code TreeNode}
     * 为根的子树添加新区间。
     *
     * @param tree     {@link IntervalTree} 包含该子树。主要用于
     *                 内部记录，例如按需调整树的大小。
     * @param root     我们正在向其添加新区间的子树的根。
     * @param interval 我们要添加的 {@link Interval}。
     * @param <T>      区间起点和终点的类型。
     * @return 子树的新根。如果操作后子树需要再平衡，
     * 它可能与当前根不同。
     */
    public static <T extends Comparable<? super T>, S> TreeNode<T, S> addInterval(IntervalTree<T, S> tree, TreeNode<T, S> root, Interval<T, S> interval) {
        if (root == null) {
            tree.size++;
            return new TreeNode<>(interval);
        }
        if (interval.contains(root.midpoint)) {
            if (root.decreasing.add(interval)) {
                tree.size++;
            }
            root.increasing.add(interval);
            return root;
        } else if (interval.isLeftOf(root.midpoint)) {
            root.left = addInterval(tree, root.left, interval);
            root.height = Math.max(height(root.left), height(root.right)) + 1;
        } else {
            root.right = addInterval(tree, root.right, interval);
            root.height = Math.max(height(root.left), height(root.right)) + 1;
        }

        return root.balanceOut();
    }

    /**
     * 返回以当前节点为根的子树的高度。
     *
     * @return 以当前节点为根的子树的高度。如果节点是叶子，
     * 则为 1。
     */
    public int height() {
        return height;
    }

    /**
     * 返回以给定节点为根的子树的高度。该函数接受
     * {@code null} 值，并将其高度返回为 0。
     *
     * @param node 需要确定高度的节点。
     * @return 以 {@code node} 为根的子树的高度。如果 {@code node}
     * 为 {@code null}，则返回 0。
     */
    private static int height(TreeNode node) {
        return node == null ? 0 : node.height();
    }

    /**
     * 检查以当前节点为根的子树是否平衡，必要时
     * 对其进行平衡。
     *
     * @return 执行平衡操作后子树的新根。如果平衡是由
     * {@link #removeInterval(IntervalTree, TreeNode, Interval)} 操作触发的，
     * 且被移除的区间是子树中最后一个区间，则可能返回 {@code null}。
     */
    private TreeNode<T, S> balanceOut() {
        int balance = height(left) - height(right);
        if (balance < -1) {
            // 树偏向右侧。
            if (height(right.left) > height(right.right)) {
                this.right = this.right.rightRotate();
                return leftRotate();
            } else {
                return leftRotate();
            }
        } else if (balance > 1) {
            // 树偏向左侧。
            if (height(left.right) > height(left.left)) {
                this.left = this.left.leftRotate();
                return rightRotate();
            } else {
                return rightRotate();
            }
        } else {
            // 树已经平衡。
            return this;
        }
    }

    /**
     * 对当前节点执行左旋，即提升其右子节点
     * 并降级当前节点。左旋之后，被提升的节点会
     * {@link #assimilateOverlappingIntervals(TreeNode) assimilates} 降级节点中
     * 与其中点相交的区间。
     *
     * @return 执行旋转后以当前节点为根的子树的新根。
     */
    private TreeNode<T, S> leftRotate() {
        TreeNode<T, S> head = right;
        right = head.left;
        head.left = this;
        height = Math.max(height(right), height(left)) + 1;
        head.left = head.assimilateOverlappingIntervals(this);
        return head;
    }

    /**
     * 对当前节点执行右旋，即提升其左子节点
     * 并降级当前节点。右旋之后，被提升的节点会
     * {@link #assimilateOverlappingIntervals(TreeNode) assimilates} 降级节点中
     * 与其中点相交的区间。
     *
     * @return 执行旋转后以当前节点为根的子树的新根。
     */
    private TreeNode<T, S> rightRotate() {
        TreeNode<T, S> head = left;
        left = head.right;
        head.right = this;
        height = Math.max(height(right), height(left)) + 1;
        head.right = head.assimilateOverlappingIntervals(this);
        return head;
    }

    /**
     * 如果目标节点中的区间与当前节点的中点相交，
     * 则将这些区间全部转移到当前节点。此操作之后，
     * 目标节点可能变为空。如果是这样，则需删除它，
     * 这可能导致子树再平衡。
     *
     * @param from 将被接收区间的目标节点。
     * @return 以当前节点为根的子树的新根。
     */
    private TreeNode<T, S> assimilateOverlappingIntervals(TreeNode<T, S> from) {
        ArrayList<Interval<T, S>> tmp = new ArrayList<>();

        if (midpoint.compareTo(from.midpoint) < 0) {
            for (Interval<T, S> next : from.increasing) {
                if (next.isRightOf(midpoint)) {
                    break;
                }
                tmp.add(next);
            }
        } else {
            for (Interval<T, S> next : from.decreasing) {
                if (next.isLeftOf(midpoint)) {
                    break;
                }
                tmp.add(next);
            }
        }

        from.increasing.removeAll(tmp);
        from.decreasing.removeAll(tmp);
        increasing.addAll(tmp);
        decreasing.addAll(tmp);
        if (from.increasing.isEmpty()) {
            return deleteNode(from);
        }
        return from;
    }

    /**
     * {@link IntervalTree#query(Comparable)} 方法的辅助函数。
     * 它递归查找以当前节点为根的子树中存储的所有与
     * 目标点相交的区间。
     *
     * @param root  当前遍历子树的根。可以为 {@code null}。
     * @param point 查询点。
     * @param res   用于存储所有要返回的区间的集合。
     * @param <T,S> 区间起点和终点的类型，以及
     *              查询点的类型。
     * @return 当前子树中包含该查询的所有区间组成的集合。
     */
    public static <T extends Comparable<? super T>, S> Set<Interval<T, S>> query(TreeNode<T, S> root, T point, Set<Interval<T, S>> res) {
        if (root == null) {
            return res;
        }
        if (point.compareTo(root.midpoint) <= 0) {
            for (Interval<T, S> next : root.increasing) {
                if (next.isRightOf(point)) {
                    break;
                }
                res.add(next);
            }
            return TreeNode.query(root.left, point, res);
        } else {
            for (Interval<T, S> next : root.decreasing) {
                if (next.isLeftOf(point)) {
                    break;
                }
                res.add(next);
            }
            return TreeNode.query(root.right, point, res);
        }
    }


    /**
     * {@link IntervalTree#remove(Interval)} 方法的辅助函数。
     * 它递归查找目标区间的基准节点，如果该区间存储在
     * 基准节点中，则将其移除。与遍历所有区间相比，
     * 这是从树中移除区间更高效的方式，因为它
     * 在 O(logn) 时间内执行二分查找。
     *
     * @param tree     {@link IntervalTree} 包含该子树。主要用于
     *                 内部记录，例如按需调整树的大小。
     * @param root     当前遍历子树的根。可以为 {@code null}。
     * @param interval 要移除的目标区间。
     * @param <T,S>    区间起点和终点的类型，以及
     *                 查询点的类型。
     * @return 区间移除后以当前节点为根的子树的新根。如果该区间
     * 是子树中存储的最后一个区间，则可能为 {@code null}。
     */
    public static <T extends Comparable<? super T>, S> TreeNode<T, S> removeInterval(IntervalTree<T, S> tree, TreeNode<T, S> root, Interval<T, S> interval) {
        if (root == null) {
            return null;
        }
        if (interval.contains(root.midpoint)) {
            if (root.decreasing.remove(interval)) {
                tree.size--;
            }
            root.increasing.remove(interval);
            if (root.increasing.isEmpty()) {
                return deleteNode(root);
            }

        } else if (interval.isLeftOf(root.midpoint)) {
            root.left = removeInterval(tree, root.left, interval);
        } else {
            root.right = removeInterval(tree, root.right, interval);
        }
        return root.balanceOut();
    }

    /**
     * 从树中删除一个节点。该方法的调用方需要先检查该
     * 节点是否确实为空，因为此方法只执行删除操作。
     *
     * @param root  需要删除的节点。
     * @param <T,S> 区间起点和终点的类型。
     * @return 以被删除节点为根的子树的新根。如果被删除的节点
     * 是子树中的最后一个节点，则可能为 {@code null}。
     */
    private static <T extends Comparable<? super T>, S> TreeNode<T, S> deleteNode(TreeNode<T, S> root) {
        if (root.left == null && root.right == null) {
            return null;
        }

        if (root.left == null) {
            // 如果左子节点为空，那么右子树最多只能有一个节点，
            // 否则它就会不平衡了。因此直接返回
            // 右子节点即可。
            return root.right;
        } else {
            TreeNode<T, S> node = root.left;
            Stack<TreeNode<T, S>> stack = new Stack<>();
            while (node.right != null) {
                stack.push(node);
                node = node.right;
            }
            if (!stack.isEmpty()) {
                stack.peek().right = node.left;
                node.left = root.left;
            }
            node.right = root.right;

            TreeNode<T, S> newRoot = node;
            while (!stack.isEmpty()) {
                node = stack.pop();
                if (!stack.isEmpty()) {
                    stack.peek().right = newRoot.assimilateOverlappingIntervals(node);
                } else {
                    newRoot.left = newRoot.assimilateOverlappingIntervals(node);
                }
            }
            return newRoot.balanceOut();
        }
    }

    /**
     * 树中区间相交查询所用范围搜索的辅助方法。
     * 一旦找到一个中点包含在查询区间中的节点，它就对应范围搜索的左分支。
     * 该节点左子树中的所有区间，只要其端点大于或等于
     * 查询区间的起点，就保证与查询相交。基本上，这意味着
     * 每次在二分搜索中向左分支时，都需要把整个右子树加入结果集。
     *
     * @param node   中点包含在查询区间中的节点的左子节点。
     * @param query  查询区间。
     * @param result 存储树中所有与查询相交的区间的集合。
     */
    static <T extends Comparable<? super T>, S> void rangeQueryLeft(TreeNode<T, S> node, Interval<T, S> query, Set<Interval<T, S>> result) {
        while (node != null) {
            if (query.contains(node.midpoint)) {
                result.addAll(node.increasing);
                if (node.right != null) {
                    for (Interval<T, S> next : node.right) {
                        result.add(next);
                    }
                }
                node = node.left;
            } else {
                for (Interval<T, S> next : node.decreasing) {
                    if (next.isLeftOf(query)) {
                        break;
                    }
                    result.add(next);
                }
                node = node.right;
            }
        }
    }

    /**
     * 树中区间相交查询所用范围搜索的辅助方法。
     * 一旦找到一个中点包含在查询区间中的节点，它就对应范围搜索的右分支。
     * 该节点右子树中的所有区间，只要其端点小于或等于
     * 查询区间的终点，就保证与查询相交。基本上，这意味着
     * 每次在二分搜索中向右分支时，都需要把整个左子树加入结果集。
     *
     * @param node   中点包含在查询区间中的节点的右子节点。
     * @param query  查询区间。
     * @param result 存储树中所有与查询相交的区间的集合。
     */
    static <T extends Comparable<? super T>, S> void rangeQueryRight(TreeNode<T, S> node, Interval<T, S> query, Set<Interval<T, S>> result) {
        while (node != null) {
            if (query.contains(node.midpoint)) {
                result.addAll(node.increasing);
                if (node.left != null) {
                    for (Interval<T, S> next : node.left) {
                        result.add(next);
                    }
                }
                node = node.right;
            } else {
                for (Interval<T, S> next : node.increasing) {
                    if (next.isRightOf(query)) {
                        break;
                    }
                    result.add(next);
                }
                node = node.left;
            }
        }
    }


    /**
     * 遍历以当前节点为根的子树中所有存储区间的迭代器。
     * 遍历采用经典的中序迭代树遍历，每次迭代均为
     * 摊还 O(1) 时间。迭代器需要 O(logn) 空间——遍历的每个
     * 时刻，我们保留一个当前所遍历树分支的栈。
     */
    @Override
    public TreeNodeIterator iterator() {
        return new TreeNodeIterator();
    }

    class TreeNodeIterator implements Iterator<Interval<T, S>> {

        Stack<TreeNode<T, S>> stack = new Stack<>();
        TreeNode<T, S> subtreeRoot = TreeNode.this;
        TreeNode<T, S> currentNode;
        Interval<T, S> currentInterval;
        Iterator<Interval<T, S>> iterator = Collections.emptyIterator();

        @Override
        public boolean hasNext() {
            return subtreeRoot != null || !stack.isEmpty() || iterator.hasNext();
        }

        @Override
        public Interval<T, S> next() {
            if (!iterator.hasNext()) {
                while (subtreeRoot != null) {
                    stack.push(subtreeRoot);
                    subtreeRoot = subtreeRoot.left;
                }
                if (stack.isEmpty()) {
                    throw new NoSuchElementException();
                }
                currentNode = stack.pop();
                iterator = currentNode.increasing.iterator();
                subtreeRoot = currentNode.right;
            }
            currentInterval = iterator.next();
            return currentInterval;
        }

        @Override
        public void remove() {
            iterator.remove();
        }
    }
}
