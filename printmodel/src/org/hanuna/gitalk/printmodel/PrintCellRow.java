package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface PrintCellRow {
    public int countCell();

    @NotNull
    public ReadOnlyList<ShortEdge> getUpEdges();

    @NotNull
    public ReadOnlyList<ShortEdge> getDownEdges();

    @NotNull
    public ReadOnlyList<SpecialCell> getSpecialCell();
}
