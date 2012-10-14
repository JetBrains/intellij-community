package org.hanuna.gitalk.commitgraph;

import com.sun.istack.internal.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitLineList implements CommitLineList {
    @NotNull
    private final List<CommitLine> commitLines;

    public SimpleCommitLineList(@NotNull List<CommitLine> commitLines) {
        this.commitLines = commitLines;
    }

    @Override
    public int size() {
        return commitLines.size();
    }

    @Override
    public CommitLine get(int index) {
        return commitLines.get(index);
    }
}
