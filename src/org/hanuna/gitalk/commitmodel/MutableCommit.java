package org.hanuna.gitalk.commitmodel;

/**
 * @author erokhins
 */
public class MutableCommit implements Commit {
    private final Hash hash;
    private Commit mainParent;
    private Commit secondParent;
    private CommitData data;
    private int index;

    public MutableCommit(Hash hash) {
        this.hash = hash;
    }

    public void set(CommitData data, Commit mainParent, Commit secondParent, int index) {
        this.data = data;
        this.mainParent = mainParent;
        this.secondParent = secondParent;
        this.index = index;
    }

    @Override
    public CommitData getData() {
        return data;
    }

    @Override
    public Commit mainParent() {
        return mainParent;
    }

    @Override
    public Commit secondParent() {
        return secondParent;
    }

    @Override
    public int index() {
        return index;
    }
}
