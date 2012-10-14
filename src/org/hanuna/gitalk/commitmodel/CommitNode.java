package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

/**
 * @author erokhins
 */
public interface CommitNode {
    @NotNull
    public CommitData getData();
    @Nullable
    public CommitNode mainParent();
    @Nullable
    public CommitNode secondParent();
    public int index();
}
