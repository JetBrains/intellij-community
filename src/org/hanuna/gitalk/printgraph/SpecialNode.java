package org.hanuna.gitalk.printgraph;

import org.hanuna.gitalk.graph.Node;

/**
 * @author erokhins
 */
public class SpecialNode {
    private final Node node;
    private final int position;
    private final Type type;

    public SpecialNode(Node node, int position, Type type) {
        this.node = node;
        this.position = position;
        this.type = type;
    }

    public Node getNode() {
        return node;
    }

    public int getPosition() {
        return position;
    }

    public Type getType() {
        return type;
    }

    public static enum Type {
        commitNode,
        showNode,
        hideNode
    }
}
