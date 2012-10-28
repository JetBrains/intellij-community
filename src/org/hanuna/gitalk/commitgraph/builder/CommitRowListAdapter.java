package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.commitgraph.SpecialNode;
import org.hanuna.gitalk.commitgraph.hides.HideCommits;
import org.hanuna.gitalk.commitgraph.order.RowOfNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;

import java.util.Iterator;

/**
 * @author erokhins
 */
public class CommitRowListAdapter implements ReadOnlyList<CommitRow> {
    private final int size;
    private final VisibleNodesAndEdges nodesAndEdges;

    public CommitRowListAdapter(ReadOnlyList<RowOfNode> rows, ReadOnlyList<HideCommits> hideCommits, int size, CommitsModel commitsModel) {
        this.size = size;
        nodesAndEdges = new VisibleNodesAndEdges(rows, hideCommits, size, commitsModel);
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

            @Override
            public Commit getCommit(int position) {
                return visibleNodes.get(position).getCommit();
            }

            @Override
            public ReadOnlyList<SpecialNode> getSpecialNodes() {
                return SimpleReadOnlyList.getEmpty();
            }

            @Override
            public ReadOnlyList<Edge> getUpEdges() {
                return nodesAndEdges.getUpEdges(rowIndex);
            }

            @Override
            public ReadOnlyList<Edge> getDownEdges() {
                return nodesAndEdges.getDownEdges(rowIndex);
            }
        };
    }

    @Override
    public Iterator<CommitRow> iterator() {
        return null;
    }
}
