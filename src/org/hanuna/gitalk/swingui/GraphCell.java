package org.hanuna.gitalk.swingui;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitmodel.Commit;

/**
 * @author erokhins
 */
public class GraphCell {
    public static final int HEIGHT_CELL = 22;
    public static final int WIDTH_NODE = 15;
    public static final int CIRCLE_RADIUS = 5;
    public static final int THICK_LINE = 3;

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
