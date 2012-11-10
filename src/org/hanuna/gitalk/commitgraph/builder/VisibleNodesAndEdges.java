package org.hanuna.gitalk.commitgraph.builder;

import org.hanuna.gitalk.commitgraph.Edge;
import org.hanuna.gitalk.commitgraph.node.Node;
import org.hanuna.gitalk.commitgraph.node.PositionNode;
import org.hanuna.gitalk.commitgraph.node.SpecialNode;
import org.hanuna.gitalk.commitgraph.hidecommits.HideCommits;
import org.hanuna.gitalk.commitgraph.ordernodes.MutableRowOfNode;
import org.hanuna.gitalk.commitgraph.ordernodes.RowOfNode;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.common.readonly.SimpleReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.hanuna.gitalk.commitgraph.node.SpecialNode.Type.*;

/**
 * @author erokhins
 */
class VisibleNodesAndEdges {
    private final CommitsModel commitsModel;
    private final int size;
    private final ReadOnlyList<RowOfNode> rows;
    private final ReadOnlyList<HideCommits> hideCommitsList;

    private final CacheGet<Integer, RowOfNode> visibleNodes = new CacheGet<Integer, RowOfNode>(new Get<Integer, RowOfNode>() {
        @Override
        public RowOfNode get(Integer key) {
            return VisibleNodesAndEdges.this.calculateGetVisibleNodes(key);
        }
    }, 100);

    private final CacheGet<Integer, ReadOnlyList<Edge>> downEdges = new CacheGet<Integer, ReadOnlyList<Edge>>(new Get<Integer, ReadOnlyList<Edge>>() {
        @Override
        public ReadOnlyList<Edge> get(Integer key) {
            return VisibleNodesAndEdges.this.getDownEdgesNotCache(key);
        }
    }, 100);

    public VisibleNodesAndEdges(ReadOnlyList<RowOfNode> rows, ReadOnlyList<HideCommits> hideCommits, CommitsModel commitsModel) {
        this.rows = rows;
        this.hideCommitsList = hideCommits;
        this.commitsModel = commitsModel;
        this.size = commitsModel.size();
    }

    private void checkRowIndex(int rowIndex) {
        if (rowIndex < 0  || rowIndex >= size) {
            throw new IllegalArgumentException("incorrect rowIndex");
        }
    }

    @NotNull
    private RowOfNode calculateGetVisibleNodes(int rowIndex) {
        checkRowIndex(rowIndex);
        MutableRowOfNode row = MutableRowOfNode.create(rows.get(rowIndex));
        HideCommits hideCommits = hideCommitsList.get(rowIndex);
        for (Commit hideCommit : hideCommits) {
            row.removeNode(hideCommit);
        }
        return row;
    }

    @NotNull
    public RowOfNode getVisibleNodes(int rowIndex) {
        return visibleNodes.get(rowIndex);
    }

    @NotNull
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
        assert mainNode != null : "bad visible nodes";
        //add edge from mainNode
        ReadOnlyList<Commit> parents = mainCommit.getParents();
        for (int j = 0; j < parents.size(); j++) {
            Commit parent = parents.get(j);
            int index = nextRow.getIndexOfCommit(parent);
            assert index != -1 : "bad visible nodes";
            int colorIndex;
            if (j == 0) {
                colorIndex = mainNode.getColorIndex();
            } else {
                colorIndex = thisRow.getLastColorIndex() + j;
            }
            edges.add(new Edge(mainNode.getPosition(), index, colorIndex));
        }
        // add other edges
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

    @NotNull
    public ReadOnlyList<Edge> getDownEdges(int rowIndex) {
        return downEdges.get(rowIndex);
    }

    @NotNull
    public ReadOnlyList<Edge> getUpEdges(int rowIndex) {
        checkRowIndex(rowIndex);
        if (rowIndex == 0) {
            return SimpleReadOnlyList.getEmpty();
        } else {
            return new InverseEdges(getDownEdges(rowIndex - 1));
        }
    }

    @NotNull
    private SpecialNode createNode(@NotNull RowOfNode row, @NotNull SpecialNode.Type type, @NotNull Commit commit) {
        PositionNode node = row.getPositionNode(commit);
        assert node != null : "bad getVisibleNodes";
        return new SpecialNode(type, commit, node.getColorIndex(), node.getPosition());
    }

    @NotNull
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
