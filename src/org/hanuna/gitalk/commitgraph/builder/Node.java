package org.hanuna.gitalk.commitgraph.builder;

/**
 * @author erokhins
 */
public class Node {
    private final int commitIndex;
    private final int colorIndex;

    public Node(int commitIndex, int colorIndex) {
        this.commitIndex = commitIndex;
        this.colorIndex = colorIndex;
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    public int getColorIndex() {
        return colorIndex;
    }
}
