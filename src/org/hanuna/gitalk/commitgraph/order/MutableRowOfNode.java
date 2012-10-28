package org.hanuna.gitalk.commitgraph.order;

import org.hanuna.gitalk.commitgraph.Node;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableRowOfNode implements RowOfNode {
    @NotNull
    public static MutableRowOfNode create(RowOfNode row) {
        List<Node> nodes = new LinkedList<Node>();
        for (Node node : row) {
            assert node != null : "null Node in RowOfNode";
            nodes.add(node);
        }
        return new MutableRowOfNode(nodes, row.getRowIndex(), row.getLastColorIndex());
    }

    @NotNull
    public static MutableRowOfNode getEmpty(int rowIndex, int lastColorIndex) {
        List<Node> nodes = new LinkedList<Node>();
        return new MutableRowOfNode(nodes, rowIndex, lastColorIndex);
    }

    private final List<Node> nodes;
    private int rowIndex;
    private int lastColorIndex;

    private MutableRowOfNode(List<Node> nodes, int rowIndex, int lastColorIndex) {
        this.nodes = nodes;
        this.rowIndex = rowIndex;
        this.lastColorIndex = lastColorIndex;
    }

    @NotNull
    public Node removeNode(int index) {
        return nodes.remove(index);
    }

    public void set(int index, Node newNode) {
        nodes.set(index, newNode);
    }

    @Nullable
    public Node removeNode(@NotNull Commit commit) {
        int index = getIndexOfCommit(commit);
        if (index == -1) {
            return null;
        } else {
            return nodes.remove(index);
        }
    }

    public void addNode(int  index, @NotNull Node node) {
        nodes.add(index, node);
    }

    public void addNode(@NotNull Node node) {
        nodes.add(node);
    }

    public void setLastColorIndex(int colorIndex) {
        this.lastColorIndex = colorIndex;
    }

    public void setRowIndex(int newRowIndex) {
        this.rowIndex = newRowIndex;
    }


    @Override
    public int getRowIndex() {
        return rowIndex;
    }

    @Override
    public int getLastColorIndex() {
        return lastColorIndex;
    }

    @Override
    public int getIndexOfCommit(@NotNull Commit commit) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.getCommit().equals(commit)) {
                return i;
            }
        }
        return -1;
    }


    @Override
    public int size() {
        return nodes.size();
    }

    @NotNull
    @Override
    public Node get(int index) {
        return nodes.get(index);
    }

    @Override
    public Iterator<Node> iterator() {
        return new ReadOnlyIterator<Node>(nodes.iterator());
    }
}
