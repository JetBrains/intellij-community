package org.hanuna.gitalk.common.readonly;


import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleReadOnlyList<T> implements ReadOnlyList<T> {
    @NotNull
    public static <T> SimpleReadOnlyList<T> getEmpty() {
        return new SimpleReadOnlyList<T>(Collections.<T>emptyList());
    }

    private final List<T> elements;

    public SimpleReadOnlyList(@NotNull List<T> elements) {
        this.elements = elements;
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public T get(int index) {
        return elements.get(index);
    }

    @Override
    public Iterator<T> iterator() {
        return new ReadOnlyIterator<T>(elements.iterator());
    }
}
