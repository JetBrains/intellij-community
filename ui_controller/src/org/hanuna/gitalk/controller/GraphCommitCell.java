package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.printmodel.PrintCell;
import org.hanuna.gitalk.refs.Ref;


/**
 * @author erokhins
 */
public class GraphCommitCell extends CommitCell {
    public static final int WIDTH_NODE = 15;
    public static final int CIRCLE_RADIUS = 5;
    public static final int SELECT_CIRCLE_RADIUS = 6;
    public static final float THICK_LINE = 2.5f;
    public static final float SELECT_THICK_LINE = 3.3f;

    private final PrintCell row;

    public GraphCommitCell(PrintCell row, String text, ReadOnlyList<Ref> refsToThisCommit) {
        super(text, refsToThisCommit);
        this.row = row;
    }

    public PrintCell getRow() {
        return row;
    }

}
