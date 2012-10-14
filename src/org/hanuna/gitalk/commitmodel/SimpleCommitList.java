package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitList implements CommitList {
    @NotNull
    private final List<CommitNode> commits;

    public SimpleCommitList(List<CommitNode> commits) {
        this.commits = commits;
    }

    @Override
    public int size() {
        return commits.size();
    }

    @Override
    public CommitNode get(int index) {
        return commits.get(index);
    }
}
