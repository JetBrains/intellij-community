package org.hanuna.gitalk.commitmodel;

/**
 * @author erokhins
 */
public class MutableCommitNode implements CommitNode {
    private final Hash hash;
    private CommitNode mainParent;
    private CommitNode secondParent;
    private CommitData data;
    private int index;

    public MutableCommitNode(Hash hash) {
        this.hash = hash;
    }

    public void set(CommitData data, CommitNode mainParent, CommitNode secondParent, int index) {
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
    public CommitNode mainParent() {
        return mainParent;
    }

    @Override
    public CommitNode secondParent() {
        return secondParent;
    }

    @Override
    public int index() {
        return index;
    }
}
