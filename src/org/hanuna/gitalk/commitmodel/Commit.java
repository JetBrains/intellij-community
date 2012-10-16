package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

/**
 * @author erokhins
 */
public interface Commit {
    @NotNull
    public CommitData getData();
    @Nullable
    public Commit mainParent();
    @Nullable
    public Commit secondParent();
    public int index();
}
