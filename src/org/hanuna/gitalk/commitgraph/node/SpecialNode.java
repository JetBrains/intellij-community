package org.hanuna.gitalk.commitgraph.node;

import org.hanuna.gitalk.commitmodel.Commit;

/**
 * @author erokhins
 */
public class SpecialNode extends PositionNode {
    public static enum Type {
        Current,
        Hide,
        Show
    }

    private final Type type;

    public SpecialNode(Type type, Commit commit, int colorIndex, int position) {
        super(commit, colorIndex, position);
        this.type = type;
    }

    public Type getType() {
        return type;
    }


}
