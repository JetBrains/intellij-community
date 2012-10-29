package org.hanuna.gitalk.common;

import java.util.Iterator;

/**
 * @author erokhins
 */
public abstract class AbstractReadOnlyList<T> implements ReadOnlyList<T> {

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int i = 0;
            @Override
            public boolean hasNext() {
                return i < size();
            }

            @Override
            public T next() {
                T t = get(i);
                i++;
                return t;
            }

            @Override
            public void remove() {
                throw new IllegalAccessError("read-only iterator");
            }
        };
    }
}
