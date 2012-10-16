package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;
import org.hanuna.gitalk.common.ReadOnlyList;

/**
 * @author erokhins
 */
public interface Commit {
    @NotNull
    public CommitData getData();
    @NotNull
    public ReadOnlyList<Commit> getParents();
    public int index();
}
