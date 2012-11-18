package org.hanuna.gitalk.common.readonly;

import java.util.*;

/**
 * @author erokhins
 */
public class ReadOnlyList<E> implements List<E> {

    public static <E> ReadOnlyList<E> newReadOnlyList(List<E> list) {
        return new ReadOnlyList<E>(Collections.unmodifiableList(list));
    }

    public static <E> ReadOnlyList<E> newReadOnlyList(final SimpleAbstractList<E> abstractList) {
        List<E> list = new AbstractList<E>() {
            @Override
            public E get(int index) {
                return abstractList.get(index);
            }

            @Override
            public int size() {
                return abstractList.size();
            }
        };
        return newReadOnlyList(list);
    }

    public static interface SimpleAbstractList<E> {
        public E get(int  index);
        public int size();
    }

    private final List<E> unmodifiableList;

    private ReadOnlyList(List<E> unmodifiableList) {
        this.unmodifiableList = unmodifiableList;
    }




    @Override
    public int size() {
        return unmodifiableList.size();
    }

    @Override
    public boolean isEmpty() {
        return unmodifiableList.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return unmodifiableList.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return unmodifiableList.iterator();
    }

    @Override
    public Object[] toArray() {
        return unmodifiableList.toArray();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        return unmodifiableList.toArray(a);
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return unmodifiableList.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E get(int index) {
        return unmodifiableList.get(index);
    }

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        return unmodifiableList.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return unmodifiableList.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return unmodifiableList.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return unmodifiableList.listIterator();
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return unmodifiableList.subList(fromIndex, toIndex);
    }
}
