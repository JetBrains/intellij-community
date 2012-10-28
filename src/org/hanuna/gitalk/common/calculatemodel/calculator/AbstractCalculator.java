package org.hanuna.gitalk.common.calculatemodel.calculator;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public abstract class AbstractCalculator<M extends T, T extends Indexed> implements Calculator<T> {

    @NotNull
    @Override
    public T next(T prev) {
        assert prev.getRowIndex() + 1 < this.size() : "next is undefined";
        return next(prev, 1);
    }

    @NotNull
    @Override
    public T next(T prev, int steps) {
        assert prev.getRowIndex() + steps < this.size() && steps >= 0 : "bad count steps";
        if (steps == 0) {
            return prev;
        }
        M row = this.createMutable(prev);
        for (int i = 0; i < steps; i++) {
            row = oneStep(row);
        }
        return row;
    }

    @NotNull
    protected abstract M createMutable(T prev);

    protected abstract int size();

    @NotNull
    protected abstract M oneStep(M row);
}
