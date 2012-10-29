package org.hanuna.gitalk.commitgraph.hidecommits;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyIterator;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableHideCommits implements HideCommits {
    @NotNull
    public static MutableHideCommits create(@NotNull HideCommits hideCommits) {
        List<Commit> commits = new LinkedList<Commit>();
        for (Commit commit : hideCommits) {
            assert commit != null : "null commit in HideCommits";
            commits.add(commit);
        }
        return new MutableHideCommits(commits, hideCommits.getRowIndex());
    }

    @NotNull
    public static MutableHideCommits getEmpty(int rowIndex) {
        List<Commit> commits = new LinkedList<Commit>();
        return new MutableHideCommits(commits, rowIndex);
    }

    private final List<Commit> hideCommits;
    private int rowIndex;

    private MutableHideCommits(@NotNull List<Commit> hideCommits, int rowIndex) {
        this.hideCommits = hideCommits;
        this.rowIndex = rowIndex;
    }

    public void add(@NotNull Commit commit) {
        hideCommits.add(commit);
    }

    public void remove(@NotNull Commit commit) {
        hideCommits.remove(commit);
    }

    public void setRowIndex(int newRowIndex) {
        this.rowIndex = newRowIndex;
    }

    @Override
    public int getRowIndex() {
        return rowIndex;
    }

    @Override
    public int size() {
        return hideCommits.size();
    }

    @NotNull
    @Override
    public Commit get(int index) {
        return hideCommits.get(index);
    }

    @NotNull
    @Override
    public Iterator<Commit> iterator() {
        return new ReadOnlyIterator<Commit>(hideCommits.iterator());
    }
}
