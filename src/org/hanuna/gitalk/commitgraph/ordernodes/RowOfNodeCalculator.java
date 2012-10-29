package org.hanuna.gitalk.commitgraph.ordernodes;

import org.hanuna.gitalk.commitgraph.node.Node;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.calculator.AbstractCalculator;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class RowOfNodeCalculator extends AbstractCalculator<MutableRowOfNode, RowOfNode> {
    private final CommitsModel commitsModel;

    public RowOfNodeCalculator(@NotNull CommitsModel commitsModel) {
        this.commitsModel = commitsModel;
    }

    @NotNull
    @Override
    public RowOfNode getFirst() {
        assert commitsModel.size() > 0 : "empty CommitsModel";
        Node firstNode = new Node(commitsModel.get(0), 0);
        MutableRowOfNode firstRow = MutableRowOfNode.getEmpty(0, 0);
        firstRow.addNode(0, firstNode);
        return firstRow;
    }

    @NotNull
    @Override
    protected MutableRowOfNode createMutable(@NotNull RowOfNode prev) {
        return MutableRowOfNode.create(prev);
    }

    @Override
    protected int size() {
        return commitsModel.size();
    }

    // true, if node was add
    private boolean addNode(MutableRowOfNode row, Node node, int indexAdd) {
        int index = row.getIndexOfCommit(node.getCommit());
        if (index == -1) {
            row.addNode(indexAdd, node);
            return true;
        }
        return false;
    }

    @NotNull
    protected MutableRowOfNode oneStep(MutableRowOfNode row) {
        Commit prevCommit = commitsModel.get(row.getRowIndex());
        int prevCommitIndex = row.getIndexOfCommit(prevCommit);
        assert prevCommitIndex != -1 : "not found main commit in row";
        Node prevCommitNode = row.removeNode(prevCommitIndex);
        ReadOnlyList<Commit> parents = prevCommit.getParents();

        // add parents node if they not existed
        int countNewNode = 0;
        for (int i = 0; i < parents.size(); i++) {
            int colorIndex;
            if (i == 0) {
                colorIndex = prevCommitNode.getColorIndex();
            } else {
                colorIndex = row.getLastColorIndex() + i;
            }
            Commit parent = parents.get(i);
            if (addNode(row, new Node(parent, colorIndex), prevCommitIndex + countNewNode)) {
                countNewNode++;
            }
        }
        if (parents.size() > 0) {
            row.setLastColorIndex(row.getLastColorIndex() + parents.size() - 1);
        }
        // if mainCommit hasn't children - add him
        Commit thisCommit = commitsModel.get(row.getRowIndex() + 1);
        if (thisCommit.getChildren().size() == 0) {
            row.addNode(new Node(thisCommit, row.getLastColorIndex() + 1));
            row.setLastColorIndex(row.getLastColorIndex() + 1);
        }

        row.setRowIndex(row.getRowIndex() + 1);
        return row;
    }
}
