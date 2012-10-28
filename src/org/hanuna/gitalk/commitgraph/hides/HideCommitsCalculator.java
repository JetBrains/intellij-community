package org.hanuna.gitalk.commitgraph.hides;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.calculator.AbstractCalculator;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class HideCommitsCalculator extends AbstractCalculator<MutableHideCommits, HideCommits> {
    private final CommitsModel commitsModel;

    public HideCommitsCalculator(CommitsModel commitsModel) {
        this.commitsModel = commitsModel;
    }

    @NotNull
    @Override
    public HideCommits getFirst() {
        return MutableHideCommits.getEmpty(0);
    }

    @NotNull
    @Override
    protected MutableHideCommits createMutable(HideCommits prev) {
        return MutableHideCommits.create(prev);
    }

    @Override
    protected int size() {
        return commitsModel.size();
    }

    @NotNull
    protected MutableHideCommits oneStep(MutableHideCommits hideCommits) {
        ReadOnlyList<Commit> hides = commitsModel.hidesCommits(hideCommits.getRowIndex());
        ReadOnlyList<Commit> shows = commitsModel.showsCommits(hideCommits.getRowIndex());
        for (Commit hide : hides) {
            hideCommits.add(hide);
        }
        for (Commit show : shows) {
            hideCommits.remove(show);
        }
        hideCommits.setRowIndex(hideCommits.getRowIndex() + 1);
        return hideCommits;
    }
}
