package org.hanuna.gitalk.commitgraph;

import org.hanuna.gitalk.commitgraph.node.SpecialNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CommitRow {
    public int count();

    @NotNull
    public Commit getCommit(int position);

    @NotNull
    public ReadOnlyList<SpecialNode> getSpecialNodes();

    @NotNull
    public ReadOnlyList<Edge> getUpEdges();

    @NotNull
    public ReadOnlyList<Edge> getDownEdges();
}
