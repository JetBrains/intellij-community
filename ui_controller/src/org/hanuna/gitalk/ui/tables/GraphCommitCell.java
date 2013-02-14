package org.hanuna.gitalk.ui.tables;

import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.refs.Ref;

import java.util.List;


/**
 * @author erokhins
 */
public class GraphCommitCell extends CommitCell {
    public static final int WIDTH_NODE = 15;
    public static final int CIRCLE_RADIUS = 5;
    public static final int SELECT_CIRCLE_RADIUS = 6;
    public static final float THICK_LINE = 2.5f;
    public static final float SELECT_THICK_LINE = 3.3f;

    private final GraphPrintCell row;

    public GraphCommitCell(GraphPrintCell row, String text, List<Ref> refsToThisCommit) {
        super(text, refsToThisCommit);
        this.row = row;
    }

    public GraphPrintCell getPrintCell() {
        return row;
    }

}
