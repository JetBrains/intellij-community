package org.hanuna.gitalk.commitmodel;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Commit {
    public int index();

    @NotNull
    public Hash hash();

    @NotNull
    public ReadOnlyList<Commit> getParents();

    // count new Commits, which have link from reader commits
    public int countNewCommits();

    @NotNull
    public String getMessage();

    @NotNull
    public String getAuthor();

    public long getTimeStamp();
}
