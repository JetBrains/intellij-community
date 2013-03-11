package org.hanuna.gitalk.swing_ui.frame.refs;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class CommitSelectManager {
    private final Set<Hash> selectCommits = new HashSet<Hash>();

    public boolean isSelect(@NotNull Hash commitHash) {
        return selectCommits.contains(commitHash);
    }

    public void setSelectCommit(@NotNull Hash commitHash, boolean  select) {
        if (select) {
            selectCommits.add(commitHash);
        } else {
            selectCommits.remove(commitHash);
        }
    }



}
