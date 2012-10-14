package org.hanuna.gitalk.commitgraph.builder;

/**
 * @author erokhins
 */
public class GraphNode {
    private final int indexCommit;
    private final int indexColor;

    public GraphNode(int indexCommit, int indexColor) {
        this.indexCommit = indexCommit;
        this.indexColor = indexColor;
    }

    public int getIndexCommit() {
        return indexCommit;
    }

    public int getIndexColor() {
        return indexColor;
    }
}
