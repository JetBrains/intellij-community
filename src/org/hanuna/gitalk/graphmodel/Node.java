package org.hanuna.gitalk.graphmodel;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graphmodel.select.Select;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Node extends Select {

    @NotNull
    public Type getType();

    @NotNull
    public NodeRow getRow();

    @NotNull
    public ReadOnlyList<Edge> getUpEdges();

    @NotNull
    public ReadOnlyList<Edge> getDownEdges();

    /**
     *
     * @return if type == commitNode - this commit.
     * if type == edgeNode - common Parent
     * if type == endCommitNode - parent of This Commit
     */
    @NotNull
    public Commit getCommit();

    @NotNull
    public Branch getBranch();

    /**
     * @return index in log without hide branches
     */
    public int getLogIndex();

    public static enum Type{
        commitNode,
        edgeNode,
        endCommitNode
    }

}
