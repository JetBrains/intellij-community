package org.hanuna.gitalk.graphmodel;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;

/**
 * @author erokhins
 */
public interface NodeRow extends ReadOnlyList<Node> {
    /**
     * @return need for Node in this row, (determinate row index Of Node)
     */
    public int getRowIndex();
}
