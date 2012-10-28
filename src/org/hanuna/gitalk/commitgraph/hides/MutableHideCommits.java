package org.hanuna.gitalk.commitgraph.hides;

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

    public static MutableHideCommits create(HideCommits hideCommits) {
        List<Commit> commits = new LinkedList<Commit>();
        for (Commit commit : hideCommits) {
            commits.add(commit);
        }
        return new MutableHideCommits(commits, hideCommits.getRowIndex());
    }

    public static MutableHideCommits getEmpty(int rowIndex) {
        List<Commit> commits = new LinkedList<Commit>();
        return new MutableHideCommits(commits, rowIndex);
    }

    private final List<Commit> hideCommits;
    private int rowIndex;

    private MutableHideCommits(List hideCommits, int rowIndex) {
        this.hideCommits = hideCommits;
        this.rowIndex = rowIndex;
    }

    public void add(Commit commit) {
        hideCommits.add(commit);
    }

    public void remove(Commit commit) {
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

    @Override
    public Iterator<Commit> iterator() {
        return new ReadOnlyIterator<Commit>(hideCommits.iterator());
    }
}
