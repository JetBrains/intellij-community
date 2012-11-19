package org.hanuna.gitalk.printgraph;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface PrintGraphRow {
    public int size();

    @NotNull
    public ReadOnlyList<ShortEdge> getUpEdges(int rowIndex);

    @NotNull
    public ReadOnlyList<ShortEdge> getDownEdges(int rowIndex);

    @NotNull
    public ReadOnlyList<SpecialNode> getSpecialNodes(int rowIndex);
}
