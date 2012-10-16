package org.hanuna.gitalk.commitgraph.builder;

import com.sun.istack.internal.NotNull;
import org.hanuna.gitalk.commitgraph.CommitRow;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitRowListBuilder {
    private final List<RowOfNode> rows = new ArrayList<RowOfNode>();
    private final ReadOnlyList<Commit> commits;
    private int indexColor = 0;

    public CommitRowListBuilder(@NotNull ReadOnlyList<Commit> listOfCommits) {
        this.commits = listOfCommits;
    }

    @NotNull
    public ReadOnlyList<CommitRow> build() {
        buildListLineOfNode();
        return new CommitRowListAdapter(new SimpleReadOnlyList<RowOfNode>(rows), commits);
    }

    public List<RowOfNode> buildListLineOfNode() {
        if (commits.size() > 0) {
            firstStep();
        }
        for (int i = 1; i < commits.size(); i++) {
            step(i - 1);
        }
        return rows;
    }

    /**
     *
     * @param indexCommit
     * @return count addition Edges
     */
    private int setAdditionEdges(int indexCommit) {
        Commit commit = commits.get(indexCommit);
        int countParents = commit.getParents().size();
        int additionEdges = (countParents == 0) ? 0 : countParents - 1;
        indexColor += additionEdges;
        return additionEdges;
    }

    private void firstStep() {
        MutableRowOfNode firstRow = new MutableRowOfNode();
        firstRow.add(0, indexColor);
        indexColor++;
        firstRow.setMainPosition(0);
        firstRow.setStartIndexColor(indexColor);
        firstRow.setCountAdditionEdges(setAdditionEdges(0));
        rows.add(firstRow);
    }


    private void step(int indexPrevRow) {
        RowOfNode prevRow = rows.get(indexPrevRow);
        MutableRowOfNode nextRow = new MutableRowOfNode();
        for (Node node : prevRow) {
            int commitIndex = node.getCommitIndex();
            if (commitIndex != indexPrevRow) {
                nextRow.add(commitIndex, node.getColorIndex());
            } else {
                ReadOnlyList<Commit> parents = commits.get(commitIndex).getParents();
                if (parents.size() > 0) {
                    nextRow.add(parents.get(0).index(), node.getColorIndex());
                }
                int startIndexColor = prevRow.getStartIndexColor();
                for (int i = 1; i < parents.size(); i++) {
                    Commit parent = parents.get(i);
                    nextRow.add(parent.index(), startIndexColor + i - 1);
                }
            }
        }
        int p = nextRow.getPositionOfCommit(indexPrevRow + 1);
        if (p == -1) {
            nextRow.add(indexPrevRow + 1, indexColor);
            indexColor++;
            nextRow.setMainPosition(nextRow.size() - 1);
        } else {
            nextRow.setMainPosition(p);
        }
        nextRow.setStartIndexColor(indexColor);
        nextRow.setCountAdditionEdges(setAdditionEdges(indexPrevRow + 1));

        rows.add(nextRow);
    }
}
