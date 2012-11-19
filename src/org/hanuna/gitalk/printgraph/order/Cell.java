package org.hanuna.gitalk.printgraph.order;

import org.hanuna.gitalk.graphmodel.Branch;

/**
 * @author erokhins
 */
public class Cell {
    private final Branch branch;
    private final int rowLogIndexOfDownNode;

    public Cell(Branch branch, int rowLogIndexOfDownNode) {
        this.branch = branch;
        this.rowLogIndexOfDownNode = rowLogIndexOfDownNode;
    }

    public Branch getBranch() {
        return branch;
    }

    public int getRowLogIndexOfDownNode() {
        return rowLogIndexOfDownNode;
    }
}
