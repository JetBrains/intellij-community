package org.hanuna.gitalk.commitgraph;

import org.hanuna.gitalk.commitgraph.node.SpecialNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;

/**
 * @author erokhins
 */
public interface CommitRow {
    public int count();
    public Commit getCommit(int position);
    public ReadOnlyList<SpecialNode> getSpecialNodes();
    public ReadOnlyList<Edge> getUpEdges();
    public ReadOnlyList<Edge> getDownEdges();
}
