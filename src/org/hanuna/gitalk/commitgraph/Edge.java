package org.hanuna.gitalk.commitgraph;

/**
 * @author erokhins
 */
public interface Edge {
    public int to();
    public int getIndexColor();
    public boolean isThick();
}
