package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface GraphPrintCellModel {

    @NotNull
    public GraphPrintCell getGraphPrintCell(final int rowIndex);

    @NotNull
    public SelectController getSelectController();

    public void recalculate(@NotNull UpdateRequest updateRequest);

    public void setLongEdgeVisibility(boolean visibility);
}
