package org.hanuna.gitalk.commitmodel.builder;

import com.sun.istack.internal.NotNull;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.common.ReadOnlyList;

/**
 * @author erokhins
 */
public class MutableCommit implements Commit {
    private CommitData data;
    private ReadOnlyList<Commit> parents;
    private int index;

    public void set(@NotNull CommitData data, @NotNull ReadOnlyList<Commit> parents, int index) {
        this.data = data;
        this.parents = parents;
        this.index = index;
    }

    @Override
    public CommitData getData() {
        return data;
    }

    @Override
    public ReadOnlyList<Commit> getParents() {
        return parents;
    }


    @Override
    public int index() {
        return index;
    }
}
