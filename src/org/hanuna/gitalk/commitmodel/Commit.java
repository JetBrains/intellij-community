package org.hanuna.gitalk.commitmodel;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 *
 */
public interface Commit {
    @NotNull
    public Hash hash();

    public boolean wasRead();

    /**
     * index() may be undefined
     * It happened, if read commit, which is parent of this commit
     */
    @Deprecated
    public int index();

    @NotNull
    public ReadOnlyList<Commit> getParents();

    @NotNull
    public String getMessage();

    @NotNull
    public String getAuthor();

    public long getTimeStamp();
}
