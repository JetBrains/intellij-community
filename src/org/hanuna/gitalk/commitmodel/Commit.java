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

    @NotNull
    public ReadOnlyList<Commit> getParents();

    public boolean hasChildren();

    // count new unique Commits among parents
    public int countNewUniqueCommitsAmongParents();

    @NotNull
    public String getMessage();

    @NotNull
    public String getAuthor();

    public long getTimeStamp();

    public boolean equals(Object obj);

    public int hashCode();


    /**
     * index() may be undefined
     * It happened, if read commit, which is parent of this commit
     */
    @Deprecated
    public int index();
}
