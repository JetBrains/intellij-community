package org.hanuna.gitalk.common.calculatemodel.calculator;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Calculator<T extends Row> {
    @NotNull
    public T getFirst();

    @NotNull
    public T next(T prev);

    // if steps == 1 is equal next(T)
    @NotNull
    public T next(T prev, int steps);
}
