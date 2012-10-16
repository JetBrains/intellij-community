package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitmodel.Commit;

/**
 * @author erokhins
 */
public class GraphCell {
    public static final int HEIGHT_CELL = 20;
    public static final int WIDTH_NODE = 10;
    public static final int CIRCLE_RADIUS = 2;

    private final Commit commit;
    private final CommitRow commitRow;

    public GraphCell(Commit commit, CommitRow commitRow) {
        this.commit = commit;
        this.commitRow = commitRow;
    }

    public Commit getCommit() {
        return commit;
    }

    public CommitRow getCommitRow() {
        return commitRow;
    }
}
