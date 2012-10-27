package org.hanuna.gitalk.common.calculatemodel;

/**
 * @author erokhins
 */
public interface Calculator<T> {
    public T next(T prev);

    // if steps == 1 is equal next(T)
    public T next(T prev, int steps);
}
