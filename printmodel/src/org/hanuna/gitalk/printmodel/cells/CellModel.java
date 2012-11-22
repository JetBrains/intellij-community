package org.hanuna.gitalk.printmodel.cells;

import org.hanuna.gitalk.common.Interval;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface CellModel {

    @NotNull
    public ReadOnlyList<CellRow> getCellRows();


    /**
     *
     * @param old rows (old.from, old.to) was changes (and may be removed)
     * @param upd row old.from -> upd.from, row old.to -> upd.to
     */
    public void update(@NotNull Interval old, @NotNull Interval upd);
}
