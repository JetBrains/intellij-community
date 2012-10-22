package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class MutableCommit implements Commit {
    private CommitData data;
    private ReadOnlyList<Commit> parents;
    private int index;
    private int countNewCommits;

    public void set(@NotNull CommitData data, @NotNull ReadOnlyList<Commit> parents, int index) {
        this.data = data;
        this.parents = parents;
        this.index = index;
        this.countNewCommits = -1;
    }


    @NotNull
    @Override
    public Hash hash() {
        return data.getHash();
    }

    @Override
    public long getTimeStamp() {
        return data.getTimeStamp();
    }

    @NotNull
    @Override
    public String getMessage() {
        return data.getCommitMessage();
    }

    @NotNull
    @Override
    public String getAuthor() {
        return data.getAuthor();
    }

    @NotNull
    @Override
    public ReadOnlyList<Commit> getParents() {
        return parents;
    }

    @Override
    public int countNewCommits() {
        return countNewCommits;
    }


    @Override
    public int index() {
        return index;
    }
}
