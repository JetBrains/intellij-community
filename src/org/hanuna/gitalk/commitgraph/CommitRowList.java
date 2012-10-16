package org.hanuna.gitalk.commitgraph;

import com.sun.istack.internal.NotNull;

/**
 * @author erokhins
 */
public interface CommitRowList {
    public int size();
    @NotNull
    public CommitRow get(int index);
}
