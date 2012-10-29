package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.commitgraph.hidecommits.HideCommits;
import org.hanuna.gitalk.commitgraph.node.SpecialNode;
import org.hanuna.gitalk.commitgraph.ordernodes.RowOfNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.AbstractReadOnlyList;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitRowListAdapter extends AbstractReadOnlyList<CommitRow> {
    private final VisibleNodesAndEdges nodesAndEdges;
    private final int size;

    public CommitRowListAdapter(ReadOnlyList<RowOfNode> rows, ReadOnlyList<HideCommits> hideCommits, CommitsModel commitsModel) {
        this.size = commitsModel.size();
        this.nodesAndEdges = new VisibleNodesAndEdges(rows, hideCommits, commitsModel);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public CommitRow get(final int rowIndex) {
        final RowOfNode visibleNodes = nodesAndEdges.getVisibleNodes(rowIndex);

        return new CommitRow() {
            @Override
            public int count() {
                return visibleNodes.size();
            }

            @NotNull
            @Override
            public Commit getCommit(int position) {
                return visibleNodes.get(position).getCommit();
            }

            @NotNull
            @Override
            public ReadOnlyList<SpecialNode> getSpecialNodes() {
                return nodesAndEdges.getSpecialNodes(rowIndex);
            }

            @NotNull
            @Override
            public ReadOnlyList<Edge> getUpEdges() {
                return nodesAndEdges.getUpEdges(rowIndex);
            }

            @NotNull
            @Override
            public ReadOnlyList<Edge> getDownEdges() {
                return nodesAndEdges.getDownEdges(rowIndex);
            }
        };
    }
}
