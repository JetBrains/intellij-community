package org.hanuna.gitalk.commitgraph;

import org.hanuna.gitalk.commitmodel.Commit;

/**
 * @author erokhins
 */
public class PositionNode extends Node {
    private final int position;

    public PositionNode(Commit commit, int colorIndex, int position) {
        super(commit, colorIndex);
        this.position = position;
    }

    public int getPosition() {
        return position;
    }
}
