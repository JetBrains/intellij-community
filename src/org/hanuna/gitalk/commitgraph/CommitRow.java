package org.hanuna.gitalk.commitgraph;

import java.util.List;

/**
 * @author erokhins
 */
public interface CommitRow {
    public int count();
    public int getIndexCommit(int position);
    public int getMainPosition();
    public List<Edge> getUpEdges(int position);
    public List<Edge> getDownEdges(int position);
}
