package org.hanuna.gitalk.commitgraph;

import com.sun.istack.internal.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitRowList implements CommitRowList {
    @NotNull
    private final List<CommitRow> commitRows;

    public SimpleCommitRowList(@NotNull List<CommitRow> commitRows) {
        this.commitRows = commitRows;
    }

    @Override
    public int size() {
        return commitRows.size();
    }

    @Override
    public CommitRow get(int index) {
        return commitRows.get(index);
    }
}
