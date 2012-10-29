package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.commitgraph.Node;
import org.hanuna.gitalk.commitgraph.PositionNode;
import org.hanuna.gitalk.commitgraph.SpecialNode;
import org.hanuna.gitalk.commitgraph.hides.HideCommits;
import org.hanuna.gitalk.commitgraph.ordernodes.MutableRowOfNode;
import org.hanuna.gitalk.commitgraph.ordernodes.RowOfNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;

import java.util.ArrayList;
import java.util.List;

import static org.hanuna.gitalk.commitgraph.SpecialNode.Type.*;

/**
 * @author erokhins
 */
class VisibleNodesAndEdges {
    private final int size;
    private final CommitsModel commitsModel;
    private final ReadOnlyList<RowOfNode> rows;
    private final ReadOnlyList<HideCommits> hideCommitsList;
    private final VisibleNodesAndEdges curC = this;

    private final CacheGet<Integer, RowOfNode> visibleNodes = new CacheGet<Integer, RowOfNode>(new Get<Integer, RowOfNode>() {
        @Override
        public RowOfNode get(Integer key) {
            return curC.getVisibleNodesNotCache(key);
        }
    }, 100);


    private final CacheGet<Integer, ReadOnlyList<Edge>> downEdges = new CacheGet<Integer, ReadOnlyList<Edge>>(new Get<Integer, ReadOnlyList<Edge>>() {
        @Override
        public ReadOnlyList<Edge> get(Integer key) {
            return curC.getDownEdgesNotCache(key);
        }
    }, 100);

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

    private RowOfNode getVisibleNodesNotCache(int rowIndex) {
        checkRowIndex(rowIndex);
        MutableRowOfNode row = MutableRowOfNode.create(rows.get(rowIndex));
        HideCommits hideCommits = hideCommitsList.get(rowIndex);
        for (Commit hideCommit : hideCommits) {
            row.removeNode(hideCommit);
        }
        return row;
    }

    public RowOfNode getVisibleNodes(int rowIndex) {
        return visibleNodes.get(rowIndex);
    }

    private ReadOnlyList<Edge> getDownEdgesNotCache(int rowIndex) {
        checkRowIndex(rowIndex);
        if (rowIndex == size - 1) {
            return SimpleReadOnlyList.getEmpty();
        }
        List<Edge> edges = new ArrayList<Edge>();
        RowOfNode thisRow = getVisibleNodes(rowIndex);
        RowOfNode nextRow = getVisibleNodes(rowIndex + 1);
        Commit mainCommit = commitsModel.get(rowIndex);
        PositionNode mainNode = thisRow.getPositionNode(mainCommit);
        assert mainNode != null : "bad hide commits model";
            ReadOnlyList<Commit> parents = mainCommit.getParents();
            for (int j = 0; j < parents.size(); j++) {
                Commit parent = parents.get(j);
                int index = nextRow.getIndexOfCommit(parent);
                assert index != -1 : "bad hide commits model";
                int colorIndex;
                if (j == 0) {
                    colorIndex = mainNode.getColorIndex();
                } else {
                    colorIndex = thisRow.getLastColorIndex() + j;
                }
                edges.add(new Edge(mainNode.getPosition(), index, colorIndex));
            }


        for (int i = 0; i < thisRow.size(); i++) {
            Node node = thisRow.get(i);
            if (node.getCommit() != mainCommit) {
                int index = nextRow.getIndexOfCommit(node.getCommit());
                if (index != -1) {
                    edges.add(new Edge(i, index, node.getColorIndex()));
                }
            }
        }
        return new SimpleReadOnlyList<Edge>(edges);
    }

    public ReadOnlyList<Edge> getDownEdges(int rowIndex) {
        return downEdges.get(rowIndex);
    }

    public ReadOnlyList<Edge> getUpEdges(int rowIndex) {
        checkRowIndex(rowIndex);
        if (rowIndex == 0) {
            return SimpleReadOnlyList.getEmpty();
        } else {
            return new InverseEdges(getDownEdges(rowIndex - 1));
        }
    }

    private SpecialNode createNode(RowOfNode row, SpecialNode.Type type, Commit commit) {
        PositionNode node = row.getPositionNode(commit);
        assert node != null : "bad getVisibleNodes";
        return new SpecialNode(type, commit, node.getColorIndex(), node.getPosition());
    }

    public ReadOnlyList<SpecialNode> getSpecialNodes(int rowIndex) {
        checkRowIndex(rowIndex);
        List<SpecialNode> list = new ArrayList<SpecialNode>();
        RowOfNode currentRow = getVisibleNodes(rowIndex);
        Commit currentCommit = commitsModel.get(rowIndex);
        list.add(createNode(currentRow, Current, currentCommit));

        ReadOnlyList<Commit> hideCommits = commitsModel.hidesCommits(rowIndex);
        for (Commit hide : hideCommits) {
            list.add(createNode(currentRow, Hide, hide));
        }

        ReadOnlyList<Commit> showCommits = commitsModel.showsCommits(rowIndex);
        for (Commit show : showCommits) {
            list.add(createNode(currentRow, Show, show));
        }

        return new SimpleReadOnlyList<SpecialNode>(list);
    }


}
