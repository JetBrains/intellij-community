package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class SpecialCell {
    private final GraphElement graphElement;
    private final int position;
    private final Type type;

    public SpecialCell(@NotNull GraphElement graphElement, int position, @NotNull Type type) {
        this.graphElement = graphElement;
        this.position = position;
        this.type = type;
    }

    @NotNull
    public GraphElement getGraphElement() {
        return graphElement;
    }

    public int getPosition() {
        return position;
    }

    @NotNull
    public Type getType() {
        return type;
    }

    public static enum Type {
        COMMIT_NODE,
        SHOW_EDGE,
        HIDE_EDGE
    }
}
