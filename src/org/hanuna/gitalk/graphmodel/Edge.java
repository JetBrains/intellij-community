package org.hanuna.gitalk.graphmodel;

import org.hanuna.gitalk.graphmodel.select.AbstractSelect;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class Edge extends AbstractSelect {
    private final Node from;
    private final Node to;
    private final Type type;
    private final Branch branch;

    public Edge(@NotNull Node from, @NotNull Node to, @NotNull Type type, Branch branch) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.branch = branch;
    }

    public Node getFrom() {
        return from;
    }

    public Node getTo() {
        return to;
    }

    public Type getType() {
        return type;
    }

    public Branch getBranch() {
        return branch;
    }

    public static enum Type{
        usual,
        hideBranch
    }
}
