package org.hanuna.gitalk.commitgraph;

import org.hanuna.gitalk.commitmodel.Commit;

/**
 * @author erokhins
 */
public class SpecialNode extends Node {
    public static enum Type {
        Current,
        Hide,
        Show
    }

    private final Type type;
    private final int position;

    public SpecialNode(Type type, Commit commit, int colorIndex, int position) {
        super(commit, colorIndex);
        this.type = type;
        this.position = position;
    }

    public Type getType() {
        return type;
    }

    public int getPosition() {
        return position;
    }

}
