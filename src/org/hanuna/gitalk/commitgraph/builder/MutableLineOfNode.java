package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.common.MyAssert;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableLineOfNode implements LineOfNode {
    private final List<GraphNode> nodes = new ArrayList<GraphNode>();
    private int mainPosition = -1;
    private int additionColor = -1;

    public void setMainPosition(int position) {
        this.mainPosition = position;
    }

    public void setAdditionColor(int color) {
        this.additionColor = color;
    }

    public int getPositionOfCommit(int indexCommit) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getIndexCommit() == indexCommit) {
                return i;
            }
        }
        return -1;
    }

    public void add(int indexCommit, int indexColor) {
        if (getPositionOfCommit(indexCommit) == -1) {
            nodes.add(new GraphNode(indexCommit, indexColor));
        }
    }

    @Override
    public int size() {
        return nodes.size();
    }

    @Override
    public int getMainPosition() {
        return mainPosition;
    }

    @Override
    public int getAdditionColor() {
        return additionColor;
    }

    @Override
    public Iterator<GraphNode> iterator() {
        final Iterator<GraphNode> it = nodes.iterator();
        return new Iterator<GraphNode>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public GraphNode next() {
                return it.next();
            }

            @Override
            public void remove() {
                throw new MyAssert("it is read-only iterator");
            }
        };
    }

}
