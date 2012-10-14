package org.hanuna.gitalk.commitgraph;

import com.sun.istack.internal.NotNull;

/**
 * @author erokhins
 */
public interface CommitLineList {
    public int size();
    @NotNull
    public CommitLine get(int index);
}
