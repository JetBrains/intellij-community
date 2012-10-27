package org.hanuna.gitalk.common.calculatemodel;

import org.hanuna.gitalk.common.ReadOnlyList;

/**
 * @author erokhins
 */
public interface CalculateModel<T> extends ReadOnlyList<T>{

    // before run this method all methods ReadOnlyList must throw Exception
    public void prepare(T first, Calculator<T> calculator, int size);
    public void updateSize(int newSize);
    public void updateSize(int startUpdateIndex, int newSize);
}
