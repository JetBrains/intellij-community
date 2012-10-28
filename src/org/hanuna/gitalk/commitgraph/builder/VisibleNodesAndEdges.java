package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.commitgraph.hides.HideCommits;
import org.hanuna.gitalk.commitgraph.order.MutableRowOfNode;
import org.hanuna.gitalk.commitgraph.Node;
import org.hanuna.gitalk.commitgraph.order.RowOfNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
class VisibleNodesAndEdges {
    private final int size;
    private final CommitsModel commitsModel;
    private final ReadOnlyList<RowOfNode> rows;
    private final ReadOnlyList<HideCommits> hideCommitsList;

    public VisibleNodesAndEdges(ReadOnlyList<RowOfNode> rows, ReadOnlyList<HideCommits> hideCommits, int size, CommitsModel commitsModel) {
        this.size = size;
        this.rows = rows;
        this.hideCommitsList = hideCommits;
        this.commitsModel = commitsModel;
    }

    private void checkRowIndex(int rowIndex) {
        if (rowIndex < 0  || rowIndex >= size) {
            throw new IllegalArgumentException("incorrect rowIndex");
        }
    }

    public RowOfNode getVisibleNodes(int rowIndex) {
        checkRowIndex(rowIndex);
        MutableRowOfNode row = MutableRowOfNode.create(rows.get(rowIndex));
        HideCommits hideCommits = hideCommitsList.get(rowIndex);
        for (Commit hideCommit : hideCommits) {
            row.removeNode(hideCommit);
        }
        return row;
    }

    public ReadOnlyList<Edge> getDownEdges(int rowIndex) {
        checkRowIndex(rowIndex);
        if (rowIndex == size - 1) {
            return SimpleReadOnlyList.getEmpty();
        }
        List<Edge> edges = new ArrayList<Edge>();
        RowOfNode thisRow = getVisibleNodes(rowIndex);
        RowOfNode nextRow = getVisibleNodes(rowIndex + 1);
        Commit mainCommit = commitsModel.get(rowIndex);
        for (int i = 0; i < thisRow.size(); i++) {
            Node node = thisRow.get(i);
            if (node.getCommit() == mainCommit) {
                ReadOnlyList<Commit> parents = mainCommit.getParents();
                for (int j = 0; j < parents.size(); j++) {
                    Commit parent = parents.get(j);
                    int index = nextRow.getIndexOfCommit(parent);
                    assert index != -1 : "bad hide commits model";
                    int colorIndex;
                    if (j == 0) {
                        colorIndex = node.getColorIndex();
                    } else {
                        colorIndex = thisRow.getLastColorIndex() + j;
                    }
                    edges.add(new Edge(i, index, colorIndex));
                }
            } else {
                int index = nextRow.getIndexOfCommit(node.getCommit());
                if (index != -1) {
                    edges.add(new Edge(i, index, node.getColorIndex()));
                }
            }
        }
        return new SimpleReadOnlyList<Edge>(edges);
    }

    public ReadOnlyList<Edge> getUpEdges(int rowIndex) {
        checkRowIndex(rowIndex);
        if (rowIndex == 0) {
            return SimpleReadOnlyList.getEmpty();
        } else {
            return new InverseEdges(getDownEdges(rowIndex - 1));
        }
    }


}
