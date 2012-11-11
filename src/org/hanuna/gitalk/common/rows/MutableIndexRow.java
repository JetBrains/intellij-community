package org.hanuna.gitalk.common.rows;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;

import java.util.Iterator;

/**
 * @author erokhins
 */
public class MutableIndexRow<T> implements Row<T> {
    private final ReadOnlyList<T> elements;
    private int rowIndex = 0;

    public MutableIndexRow(ReadOnlyList<T> elements) {
        this.elements = elements;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    @Override
    public int getRowIndex() {
        return rowIndex;
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public T get(int index) {
        return elements.get(index);
    }

    @Override
    public Iterator<T> iterator() {
        return elements.iterator();
    }
}
