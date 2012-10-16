package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;

/**
 * @author erokhins
 */
public interface CommitList {
    public int size();

    @NotNull
    public Commit get(int index);
}
