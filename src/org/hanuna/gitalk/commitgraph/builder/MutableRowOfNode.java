package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.common.ReadOnlyIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableRowOfNode implements RowOfNode {
    private final List<Node> nodes = new ArrayList<Node>();
    private int mainPosition = -1;
    private int setStartIndexColor = -1;
    private int countAdditionEdges = 0;

    public void setMainPosition(int position) {
        this.mainPosition = position;
    }

    public void setStartIndexColor(int color) {
        this.setStartIndexColor = color;
    }

    public void setCountAdditionEdges(int countAdditionEdges) {
        this.countAdditionEdges = countAdditionEdges;
    }


    public int getPositionOfCommit(int indexCommit) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getCommitIndex() == indexCommit) {
                return i;
            }
        }
        return -1;
    }

    public void add(int indexCommit, int indexColor) {
        if (getPositionOfCommit(indexCommit) == -1) {
            nodes.add(new Node(indexCommit, indexColor));
        }
    }

    @Override
    public int size() {
        return nodes.size();
    }

    @Override
    public Node getNode(int position) {
        return nodes.get(position);
    }

    @Override
    public int getMainPosition() {
        return mainPosition;
    }

    @Override
    public int getStartIndexColor() {
        return setStartIndexColor;
    }

    @Override
    public int getCountAdditionEdges() {
        return countAdditionEdges;
    }

    @Override
    public Iterator<Node> iterator() {
        final Iterator<Node> it = nodes.iterator();
        return new ReadOnlyIterator<Node>(nodes.iterator());
    }

}
