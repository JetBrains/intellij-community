package org.hanuna.gitalk.common.calculatemodel;

import org.hanuna.gitalk.common.ReadOnlyIterator;
import org.hanuna.gitalk.common.calculatemodel.calculator.Calculator;
import org.hanuna.gitalk.common.calculatemodel.calculator.Row;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class FullPreCalculateModel<T extends Row> implements CalculateModel<T> {
    private final List<T> calcList;
    private int size = -1;

    public FullPreCalculateModel() {
        this.calcList = new ArrayList<T>();
    }

    public FullPreCalculateModel(int size) {
        this.calcList = new ArrayList<T>(size);
    }

    private void checkPrepare() {
        assert size >= 0 : "not prepare";
    }

    @Override
    public void prepare(Calculator<T> calculator, int size) {
        assert size >= 0 : "bad size";
        T first = calculator.getFirst();
        this.size = size;
        calcList.add(first);
        T t = first;
        for (int i = 1; i < size; i++) {
            t = calculator.next(t);
            calcList.add(t);
        }
    }

    @Override
    public int size() {
        checkPrepare();
        return size;
    }

    @Override
    public T get(int index) {
        checkPrepare();
        return calcList.get(index);
    }

    @Override
    public Iterator<T> iterator() {
        checkPrepare();
        return new ReadOnlyIterator<T>(calcList.iterator());
    }
}
