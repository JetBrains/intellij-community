package org.hanuna.gitalk.commitgraph.builder;

import com.sun.istack.internal.NotNull;
import org.hanuna.gitalk.commitgraph.CommitRowList;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitRowListBuilder {
    private final List<RowOfNode> rows = new ArrayList<RowOfNode>();
    private final CommitList listOfCommits;
    private int indexColor = 0;

    public CommitRowListBuilder(@NotNull CommitList listOfCommits) {
        this.listOfCommits = listOfCommits;
    }

    @NotNull
    public CommitRowList build() {
        buildListLineOfNode();
        return new CommitRowListAdapter(rows, listOfCommits);
    }

    public List<RowOfNode> buildListLineOfNode() {
        if (listOfCommits.size() > 0) {
            firstStep();
        }
        for (int i = 1; i < listOfCommits.size(); i++) {
            step(i);
        }
        return rows;
    }

    private void firstStep() {
        MutableRowOfNode firstLine = new MutableRowOfNode();
        firstLine.add(0, indexColor);
        indexColor++;
        firstLine.setMainPosition(0);
        if (listOfCommits.get(0).secondParent() != null) {
            firstLine.setAdditionColor(indexColor);
            indexColor++;
        }
        rows.add(firstLine);
    }

    private void step(int index) {
        RowOfNode prevRow = rows.get(index - 1);
        MutableRowOfNode nextLine = new MutableRowOfNode();
        for (GraphNode node : prevRow) {
            int indexCommit = node.getIndexCommit();
            if (indexCommit != index - 1) {
                nextLine.add(indexCommit, node.getIndexColor());
            } else {
                Commit commit = listOfCommits.get(indexCommit);
                if (commit.mainParent() != null) {
                    nextLine.add(commit.mainParent().index(), node.getIndexColor());
                }
                if (commit.secondParent() != null) {
                    nextLine.add(commit.secondParent().index(), prevRow.getAdditionColor());
                }
            }
        }
        int p = nextLine.getPositionOfCommit(index);
        if (p == -1) {
            nextLine.add(index, indexColor);
            indexColor++;
            nextLine.setMainPosition(nextLine.size() - 1);
        } else {
            nextLine.setMainPosition(p);
        }
        if (listOfCommits.get(index).secondParent() != null) {
            nextLine.setAdditionColor(indexColor);
            indexColor++;
        }
        rows.add(nextLine);
    }
}
