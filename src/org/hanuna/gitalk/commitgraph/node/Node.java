package org.hanuna.gitalk.commitgraph.node;

import org.hanuna.gitalk.commitmodel.Commit;

/**
 * @author erokhins
 */
public class Node {
    private final Commit commit;
    private final int colorIndex;

    public Node(Commit commit, int colorIndex) {
        this.commit = commit;
        this.colorIndex = colorIndex;
    }

    public Commit getCommit() {
        return commit;
    }

    public int getColorIndex() {
        return colorIndex;
    }


}
