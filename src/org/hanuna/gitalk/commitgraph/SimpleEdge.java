package org.hanuna.gitalk.commitgraph;

/**
 * @author erokhins
 */
public class SimpleEdge implements Edge {
    private final int to;
    private final int indexColor;

    public SimpleEdge(int to, int indexColor) {
        this.to = to;
        this.indexColor = indexColor;
    }

    @Override
    public int to() {
        return to;
    }

    @Override
    public int getIndexColor() {
        return indexColor;
    }

    @Override
    public boolean isThick() {
        return false;
    }
}
