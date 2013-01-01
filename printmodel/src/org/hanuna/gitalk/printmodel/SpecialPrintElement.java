package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.graph.elements.GraphElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class SpecialPrintElement {
    private final GraphElement graphElement;
    private final int position;
    private final Type type;
    private final boolean selected;

    public SpecialPrintElement(@NotNull GraphElement graphElement, int position, @NotNull Type type, boolean selected) {
        this.graphElement = graphElement;
        this.position = position;
        this.type = type;
        this.selected = selected;
    }

    @NotNull
    public GraphElement getGraphElement() {
        return graphElement;
    }

    public int getPosition() {
        return position;
    }

    public boolean isSelected() {
        return selected;
    }

    @NotNull
    public Type getType() {
        return type;
    }

    public static enum Type {
        COMMIT_NODE,
        UP_ARROW,
        DOWN_ARROW
    }
}
