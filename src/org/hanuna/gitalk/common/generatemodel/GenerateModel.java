package org.hanuna.gitalk.common.generatemodel;

import org.hanuna.gitalk.common.Interval;
import org.hanuna.gitalk.common.generatemodel.generator.Generator;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface GenerateModel<T> {
    @NotNull
    public ReadOnlyList<T> getList();

    public void prepare(Generator<T> generator, T firstRow, int size);

    /**
     *  [a, b) -> [a, d) i.e. 1, 2, 3... a-1, a, ... b-1, b, .. -> 1, 2...a-1, {a}, {a+1}... {d-1}, d, d+1...
     *  regenerate - {i}
     */
    public void update(Interval oldInterval, Interval newInterval);
}
