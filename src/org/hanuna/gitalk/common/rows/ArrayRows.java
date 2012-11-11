package org.hanuna.gitalk.common.rows;

import org.hanuna.gitalk.common.readonly.AbstractReadOnlyList;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author erokhins
 */
public class ArrayRows<T> extends AbstractReadOnlyList<Row<T>> implements Rows<T> {
    private final SpecialArrayList<MutableIndexRow<T>> rows;

    private ArrayRows(SpecialArrayList<MutableIndexRow<T>> rows) {
        this.rows = rows;
    }

    private ArrayRows() {
        this(new SpecialArrayList<MutableIndexRow<T>>());
    }


    private void fixRowIndex(int startIndex) {
        for (int i = startIndex; i < size(); i++) {
            MutableIndexRow<T> row = rows.get(i);
            row.setRowIndex(i);
        }
    }

    @Override
    public void rewriteInterval(Interval interval, final ReadOnlyList<Row<T>> newElements) {
        rows.removeInterval(interval);
        ReadOnlyList<MutableIndexRow<T>> newMutableElements = new AbstractReadOnlyList<MutableIndexRow<T>>() {
            @Override
            public int size() {
                return newElements.size();
            }

            @Override
            public MutableIndexRow<T> get(int index) {
                Row<T> row = newElements.get(index);
                if (row instanceof MutableIndexRow) {
                    return (MutableIndexRow<T>) row;
                } else {
                    return new MutableIndexRow<T>(row);
                }
            }
        };
        rows.addReadOnlyList(interval.from(), newMutableElements);
        fixRowIndex(interval.from());
    }

    @Override
    public void add(Row<T> element) {
        MutableIndexRow<T> elm;
        if (element instanceof MutableIndexRow) {
            elm = (MutableIndexRow<T>) element;
        } else {
            elm = new MutableIndexRow<T>(element);
        }
        elm.setRowIndex(rows.size());
        rows.add(elm);
    }

    @Override
    public int size() {
        return rows.size();
    }

    @Override
    public Row<T> get(int index) {
        return rows.get(index);
    }

    private static class SpecialArrayList<T> extends ArrayList<T> {
        public SpecialArrayList() {
            super();
        }

        public void removeInterval(Interval interval) {
            removeRange(interval.from(), interval.to());
        }

        public void addReadOnlyList(int index, final ReadOnlyList<T> elements) {
            addAll(index, new AbstractCollection<T>() {
                @Override
                public Iterator<T> iterator() {
                    return elements.iterator();
                }

                @Override
                public int size() {
                    return elements.size();
                }
            });
        }
    }

}
