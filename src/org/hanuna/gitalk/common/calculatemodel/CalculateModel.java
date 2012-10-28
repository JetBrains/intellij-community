package org.hanuna.gitalk.common.calculatemodel;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.calculator.Calculator;
import org.hanuna.gitalk.common.calculatemodel.calculator.Indexed;

/**
 * @author erokhins
 */
public interface CalculateModel<T extends Indexed> extends ReadOnlyList<T>{

    // before run this method all methods ReadOnlyList must throw Exception
    public void prepare(Calculator<T> calculator, int size);
    public void updateSize(int newSize);
    public void updateSize(int startUpdateIndex, int newSize);
}
