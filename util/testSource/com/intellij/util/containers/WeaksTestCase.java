package com.intellij.util.containers;

import junit.framework.TestCase;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

public abstract class WeaksTestCase extends TestCase {
  protected final ArrayList myHolder = new ArrayList();

  protected void checkSameElements(Iterator holderIterator, Iterator collectionIterator) {
    while (holderIterator.hasNext() && collectionIterator.hasNext()) {
      assertSame(holderIterator.next(), collectionIterator.next());
    }
    assertEquals(holderIterator.hasNext(), collectionIterator.hasNext());
  }

  //protected void checkSameElements(ArrayList elementsHolder, WeakCollection collection) {
  //  for (int i = 0; i < elementsHolder.size(); i++) {
  //    assertSame(String.valueOf(i), elementsHolder.get(i), collection.get(i));
  //  }
  //  final Iterator iterator = elementsHolder.iterator();
  //  collection.forEach(new WeakCollection.Procedure() {
  //    public boolean execute(Object object, Iterator it) {
  //      assertSame(iterator.next(), object);
  //      return true;
  //    }
  //  });
  //}

  protected void gc() {
    System.gc();
    int[][] ints = new int[1000000][];
    for(int i = 0; i < 1000000; i++){
      ints[i] = new int[0];
    }

    System.gc();
    ints = null;
    System.gc();

    WeakReference weakReference = new WeakReference(new Object());
    do {
      System.gc();
    }
    while (weakReference.get() != null);
  }


  protected void checkSameElements(WeakReferenceArray collection) {
    checkSameNotNulls(collection);
    assertEquals(myHolder.size(), collection.size());
    for (int i = 0; i < myHolder.size(); i++) {
      assertSame(myHolder.get(i), collection.get(i));
    }
    WeakReference[] references = collection.getReferences();
    for (int i = myHolder.size(); i < references.length; i++)
      assertNull(references[i]);
  }

  protected void checkSameNotNulls(WeakReferenceArray collection) {
    int validIndex = -1;
    int validCount = 0;
    for (int i = 0; i < myHolder.size(); i++) {
      validIndex = nextValidIndex(validIndex, collection);
      assertSame(String.valueOf(i) + "==" + validIndex, myHolder.get(i), collection.get(validIndex));
      validCount++;
    }
    assertEquals(myHolder.size(), validCount);
    assertEquals(collection.size(), nextValidIndex(validIndex, collection));
    assertEquals(collection.size() - myHolder.size(), collection.getCorpseCount());

    //validIndex = Math.max(validIndex, 0);
    WeakReference[] references = collection.getReferences();
    for (int i = collection.size(); i < references.length; i++)
      assertNull(references[i]);
  }

  private int nextValidIndex(int validIndex, WeakReferenceArray collection) {
    validIndex++;
    while (validIndex < collection.size() && collection.get(validIndex) == null) validIndex++;
    return validIndex;
  }

  protected void addElement(Object o, WeakReferenceArray array) {
    myHolder.add(o);
    array.add(o);
  }
}
