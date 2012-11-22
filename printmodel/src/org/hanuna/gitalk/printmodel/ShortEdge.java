package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.graph.Edge;

/**
 * @author erokhins
 */
public class ShortEdge {
    private final Edge edge;
    private final int upPosition;
    private final int downPosition;

    public ShortEdge(Edge edge, int upPosition, int downPosition) {
        this.edge = edge;
        this.upPosition = upPosition;
        this.downPosition = downPosition;
    }

    public Edge getEdge() {
        return edge;
    }

    public int getUpPosition() {
        return upPosition;
    }

    public int getDownPosition() {
        return downPosition;
    }
}
