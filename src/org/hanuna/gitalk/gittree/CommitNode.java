package org.hanuna.gitalk.gittree;

/**
 * @author erokhins
 */
public class CommitNode {
    private CommitData commitData;
    private int logIndex;
    private CommitNode mainParent = null;
    private CommitNode secondParent = null;



    public void setData(CommitData commitData) {
        this.commitData = commitData;
    }

    public CommitData getData() {
        return commitData;
    }


    public void setLogIndex(int logIndex) {
        this.logIndex = logIndex;
    }

    public int getLogIndex() {
        return logIndex;
    }


    public void setMainParent(CommitNode mainParent) {
        this.mainParent = mainParent;
    }

    public void setSecondParent(CommitNode secondParent) {
        this.secondParent = secondParent;
    }


    public CommitNode getMainParent() {
        return mainParent;
    }

    public CommitNode getSecondParent() {
        return secondParent;
    }



}
