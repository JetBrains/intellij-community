package org.hanuna.gitalk.common.rows;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;

/**
 * @author erokhins
 */
public interface Rows<T> extends ReadOnlyList<Row<T>> {

    /**
     * @param interval remove elements interval. [from, to)
     * @param newElements add elements after interval.from(). Index first added element will be interval.from()
     */
    public void rewriteInterval(Interval interval, ReadOnlyList<Row<T>> newElements);
    public void add(Row<T> element);
}
