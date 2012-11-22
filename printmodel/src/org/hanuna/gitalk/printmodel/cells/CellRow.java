package org.hanuna.gitalk.printmodel.cells;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.NodeRow;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CellRow {
    @NotNull
    public ReadOnlyList<Cell> getCells();
    public NodeRow getGraphRow();

}
