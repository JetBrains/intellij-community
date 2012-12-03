package org.hanuna.gitalk.printmodel.cells;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CellModel {

    @NotNull
    public ReadOnlyList<CellRow> getCellRows();

    public void update(@NotNull Replace replace);
}
