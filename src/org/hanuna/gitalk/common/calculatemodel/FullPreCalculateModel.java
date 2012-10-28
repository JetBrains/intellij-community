package org.hanuna.gitalk.common.calculatemodel;

import org.hanuna.gitalk.common.ReadOnlyIterator;
import org.hanuna.gitalk.common.calculatemodel.calculator.Calculator;
import org.hanuna.gitalk.common.calculatemodel.calculator.Indexed;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class FullPreCalculateModel<T extends Indexed> implements CalculateModel<T> {
    private final List<T> calcList;
    private int size = -1;
    private Calculator<T> calculator = null;

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
    public void updateSize(int newSize) {
        checkPrepare();
        updateSize(size() - 1, newSize);
    }

    @Override
    public void updateSize(int startUpdateIndex, int newSize) {
        checkPrepare();
        assert (startUpdateIndex < size) && (startUpdateIndex < newSize) : "bad startIndexUpdate";
        for (int i = size - 1; i > startUpdateIndex; i--) {
            calcList.remove(i);
        }
        T t = get(startUpdateIndex);
        for (int i = startUpdateIndex + 1; i < newSize; i++) {
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
