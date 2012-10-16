package org.hanuna.gitalk.common;

import com.sun.istack.internal.NotNull;

import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleReadOnlyList<T> implements ReadOnlyList<T> {
    @NotNull
    private final List<T> elements;

    public SimpleReadOnlyList(List<T> elements) {
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
