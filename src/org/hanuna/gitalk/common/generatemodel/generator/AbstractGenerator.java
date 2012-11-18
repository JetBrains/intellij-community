package org.hanuna.gitalk.common.generatemodel.generator;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public abstract class AbstractGenerator<M extends T, T> implements Generator<T> {


    @NotNull
    @Override
    public T generate(T prev, int steps) {
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
    protected abstract M createMutable(T t);

    /**
     * @throws NoNext
     */
    @NotNull
    protected abstract M oneStep(M row);
}
