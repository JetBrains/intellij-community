package org.hanuna.gitalk.commitgraph;

/**
 * @author erokhins
 */
public interface Edge {
    public int to();
    public int getIndex();
    public boolean isThick();
}
