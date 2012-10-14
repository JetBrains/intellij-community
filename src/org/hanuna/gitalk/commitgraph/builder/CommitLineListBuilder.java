package org.hanuna.gitalk.commitgraph.builder;

import com.sun.istack.internal.NotNull;
import org.hanuna.gitalk.commitgraph.CommitLineList;
import org.hanuna.gitalk.commitmodel.CommitList;
import org.hanuna.gitalk.commitmodel.CommitNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitLineListBuilder {
    private final List<LineOfNode> lines = new ArrayList<LineOfNode>();
    private final CommitList listOfCommits;
    private int indexColor = 0;

    public CommitLineListBuilder(@NotNull CommitList listOfCommits) {
        this.listOfCommits = listOfCommits;
    }

    @NotNull
    public CommitLineList build() {
        buildListLineOfNode();
        return new CommitLineListAdapter(lines);
    }

    public List<LineOfNode> buildListLineOfNode() {
        if (listOfCommits.size() > 0) {
            firstStep();
        }
        for (int i = 1; i < listOfCommits.size(); i++) {
            step(i);
        }
        return lines;
    }

    private void firstStep() {
        MutableLineOfNode firstLine = new MutableLineOfNode();
        firstLine.add(0, indexColor);
        indexColor++;
        firstLine.setMainPosition(0);
        if (listOfCommits.get(0).secondParent() != null) {
            firstLine.setAdditionColor(indexColor);
            indexColor++;
        }
        lines.add(firstLine);
    }

    private void step(int index) {
        LineOfNode prevLine = lines.get(index - 1);
        MutableLineOfNode nextLine = new MutableLineOfNode();
        for (GraphNode node : prevLine) {
            int indexCommit = node.getIndexCommit();
            if (indexCommit != index - 1) {
                nextLine.add(indexCommit, node.getIndexColor());
            } else {
                CommitNode commit = listOfCommits.get(indexCommit);
                if (commit.mainParent() != null) {
                    nextLine.add(commit.mainParent().index(), node.getIndexColor());
                }
                if (commit.secondParent() != null) {
                    nextLine.add(commit.secondParent().index(), prevLine.getAdditionColor());
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
            if (listOfCommits.get(index).secondParent() != null) {
                nextLine.setAdditionColor(indexColor);
                indexColor++;
            }
        }
        lines.add(nextLine);
    }
}
