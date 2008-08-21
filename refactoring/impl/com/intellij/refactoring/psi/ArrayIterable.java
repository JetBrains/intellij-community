package com.intellij.refactoring.psi;

import java.util.Iterator;

class ArrayIterable<T> implements Iterable<T>
{
    private final T[] contents;

    public ArrayIterable(T[] contents){
        this.contents = contents;
    }

    public Iterator<T> iterator(){
        return new ArrayIterator<T>(contents);
    }
}
