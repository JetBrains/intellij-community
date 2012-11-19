package org.hanuna.gitalk.printgraph;

import org.hanuna.gitalk.graph.Edge;

/**
 * @author erokhins
 */
public class ShortEdge {
    private final Edge edge;
    private final int from;
    private final int to;

    public ShortEdge(Edge edge, int from, int to) {
        this.edge = edge;
        this.from = from;
        this.to = to;
    }

    public Edge getEdge() {
        return edge;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }
}
