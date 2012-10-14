package org.hanuna.gitalk.commitgraph;

/**
 * @author erokhins
 */
public interface CommitLine {
    public int countCommits();
    public int getIndexCommit(int numberOfColumn);
    public int getCurrentNumber();
}
