package org.hanuna.gitalk.commitgraph;

import java.util.List;

/**
 * @author erokhins
 */
public interface CommitLine {
    public int count();
    public int getIndexCommit(int position);
    public int getMainPosition();
    public List<Edge> getUpEdges(int position);
    public List<Edge> getDOwnEdges(int position);
}
