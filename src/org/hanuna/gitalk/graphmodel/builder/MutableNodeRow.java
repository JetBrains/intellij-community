package org.hanuna.gitalk.graphmodel.builder;

import org.hanuna.gitalk.graphmodel.Node;
import org.hanuna.gitalk.graphmodel.NodeRow;

import java.util.ArrayList;

/**
 * @author erokhins
 */
public class MutableNodeRow extends ArrayList<Node> implements NodeRow {
    private int rowIndex = 0;

    public MutableNodeRow(int initialCapacity) {
        super(initialCapacity);
    }

    public MutableNodeRow() {
        this(2);
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    @Override
    public int getRowIndex() {
        return rowIndex;
    }
}
