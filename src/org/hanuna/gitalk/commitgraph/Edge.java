package org.hanuna.gitalk.commitgraph;

/**
 * @author erokhins
 */
public class Edge {
    private final int from;
    private final int to;
    private final int colorIndex;


    public Edge(int from, int to, int colorIndex) {
        this.from = from;
        this.to = to;
        this.colorIndex = colorIndex;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public int getColorIndex() {
        return colorIndex;
    }
}
