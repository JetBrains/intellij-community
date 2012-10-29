package org.hanuna.gitalk.common.calculatemodel;

import org.hanuna.gitalk.common.calculatemodel.calculator.Calculator;
import org.hanuna.gitalk.common.calculatemodel.calculator.Row;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author erokhins
 */
public class PartSaveCalculateModel<T extends Row> implements CalculateModel<T> {
    private static final int STANDART_N = 30;

    private final int N;
    private final List<T> results;
    private Calculator<T> calculator = null;
    private int size = -1;

    public PartSaveCalculateModel() {
        this.N = STANDART_N;
        this.results = new ArrayList<T>();
    }

    public PartSaveCalculateModel(int N, int initialCapacity) {
        this.N = N;
        this.results = new ArrayList<T>(initialCapacity);
    }

    private void checkPrepare() {
        assert size >= 0 : "not prepare";
    }

    @Override
    public void prepare(Calculator<T> calculator, int size) {
        assert size >= 0 : "bad size";
        this.size = size;
        this.calculator = calculator;
        T t = calculator.getFirst();
        results.add(t);
        for (int i = 1; i <= (size - 1) / N; i++) {
            t = calculator.next(t, N);
            results.add(t);
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
        assert index < size && index >= 0 : "bad index";
        int indexN = index / N;
        int additionsStep = index - indexN * N;
        T t = results.get(indexN);
        return calculator.next(t, additionsStep);
    }

    @Override
    public Iterator<T> iterator() {
        checkPrepare();
        return new Iterator<T>() {
            int step = 0;
            T t = null;
            @Override
            public boolean hasNext() {
                return step < size;
            }

            @Override
            public T next() {
                step++;
                if (t == null) {
                    t = calculator.getFirst();
                } else {
                    t = calculator.next(t);
                }
                return t;
            }

            @Override
            public void remove() {
                throw new IllegalAccessError();
            }
        };
    }
}
