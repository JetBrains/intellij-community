package org.hanuna.gitalk.commitgraph.hidecommits;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.calculator.AbstractCalculator;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class HideCommitsCalculator extends AbstractCalculator<MutableHideCommits, HideCommits> {
    private final CommitsModel commitsModel;
    private final int size;

    public HideCommitsCalculator(@NotNull CommitsModel commitsModel) {
        this.commitsModel = commitsModel;
        this.size = commitsModel.size();
    }

    @NotNull
    @Override
    public HideCommits getFirst() {
        return MutableHideCommits.getEmpty(0);
    }

    @NotNull
    @Override
    protected MutableHideCommits createMutable(@NotNull HideCommits prev) {
        return MutableHideCommits.create(prev);
    }

    @Override
    protected int size() {
        return size;
    }


    @NotNull
    protected MutableHideCommits oneStep(@NotNull MutableHideCommits hideCommits) {
        int rowIndex = hideCommits.getRowIndex();
        ReadOnlyList<Commit> hides = commitsModel.hidesCommits(rowIndex);
        ReadOnlyList<Commit> shows;
        if (rowIndex < size() - 1) {
            shows = commitsModel.showsCommits(rowIndex + 1);
        } else {
            shows = SimpleReadOnlyList.getEmpty();
        }
        for (Commit hide : hides) {
            hideCommits.add(hide);
        }
        for (Commit show : shows) {
            hideCommits.remove(show);
        }
        hideCommits.setRowIndex(rowIndex + 1);
        return hideCommits;
    }
}
