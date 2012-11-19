package org.hanuna.gitalk.printgraph.order;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Order {

    @NotNull
    public ReadOnlyList<Cell> getOrderCell(int rowLogIndex);
}
