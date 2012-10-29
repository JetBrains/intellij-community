package org.hanuna.gitalk.commitgraph.order;

import org.hanuna.gitalk.commitgraph.Node;
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

    public RowOfNodeCalculator(CommitsModel commitsModel) {
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
    protected MutableRowOfNode createMutable(RowOfNode prev) {
        return MutableRowOfNode.create(prev);
    }

    @Override
    protected int size() {
        return commitsModel.size();
    }

    // true, if node was add
    private boolean addNode(MutableRowOfNode row, Node node, int indexAdd) {
        int index = row.getIndexOfCommit(node.getCommit());
        if (index != -1) {
            return false;
        } else {
            row.addNode(indexAdd, node);
            return true;
        }
    }

    @NotNull
    protected MutableRowOfNode oneStep(MutableRowOfNode row) {
        Commit prevCommit = commitsModel.get(row.getRowIndex());
        int prevCommitIndex = row.getIndexOfCommit(prevCommit);
        assert prevCommitIndex != -1 : "bad prev row";
        Node prevNode = row.removeNode(prevCommitIndex);
        ReadOnlyList<Commit> parents = prevCommit.getParents();

        int countNewNode = 0;
        for (int i = 0; i < parents.size(); i++) {
            int colorIndex;
            if (i == 0) {
                colorIndex = prevNode.getColorIndex();
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
        Commit thisCommit = commitsModel.get(row.getRowIndex() + 1);
        if (thisCommit.getChildren().size() == 0) {
            row.addNode(new Node(thisCommit, row.getLastColorIndex() + 1));
            row.setLastColorIndex(row.getLastColorIndex() + 1);
        }

        row.setRowIndex(row.getRowIndex() + 1);
        return row;
    }
}
