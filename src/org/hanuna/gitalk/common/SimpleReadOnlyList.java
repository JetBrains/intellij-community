package org.hanuna.gitalk.common;


import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleReadOnlyList<T> implements ReadOnlyList<T> {
    public static <T> SimpleReadOnlyList<T> getEmpty() {
        return new SimpleReadOnlyList<T>(Collections.<T>emptyList());
    }

    public SimpleReadOnlyList(@NotNull List<T> elements) {
        this.elements = elements;
    }

    @NotNull
    private final List<T> elements;

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
