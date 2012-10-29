package org.hanuna.gitalk.common.readonly;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * @author erokhins
 */
public class ReadOnlyIterator<T> implements Iterator<T> {
    private final Iterator<T> iterator;

    public ReadOnlyIterator(@NotNull Iterator<T> iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public T next() {
        return iterator.next();
    }

    @Override
    public void remove() {
        throw new IllegalAccessError("read-only iterator");
    }
}
