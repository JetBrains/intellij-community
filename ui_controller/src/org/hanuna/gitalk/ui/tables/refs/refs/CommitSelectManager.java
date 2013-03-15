package org.hanuna.gitalk.ui.tables.refs.refs;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class CommitSelectManager {
    private final Set<Hash> selectCommits = new HashSet<Hash>();
    private final Hash headHash;

    public CommitSelectManager(@NotNull Hash headHash) {
        this.headHash = headHash;
    }

    public boolean isSelect(@NotNull Hash commitHash) {
        return selectCommits.contains(commitHash);
    }

    private void checkEmpty() {
        if (selectCommits.isEmpty()) {
            selectCommits.add(headHash);
        }
    }

    public void setSelectCommit(@NotNull Hash commitHash, boolean  select) {
        if (select) {
            selectCommits.add(commitHash);
        } else {
            selectCommits.remove(commitHash);
        }
        checkEmpty();
    }

    public Set<Hash> getSelectCommits() {
        return Collections.unmodifiableSet(selectCommits);
    }

    public void inverseSelectCommit(Set<Hash> commits) {
        if (selectCommits.containsAll(commits)) {
            selectCommits.removeAll(commits);
        } else {
            selectCommits.addAll(commits);
        }
        checkEmpty();
    }


}
