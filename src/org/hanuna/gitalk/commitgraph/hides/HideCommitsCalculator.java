package org.hanuna.gitalk.commitgraph.hides;

import org.hanuna.gitalk.commitmodel.CommitsModel;
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
        return MutableHideCommits.getEmpty(hideCommits.getRowIndex() + 1);
    }
}
