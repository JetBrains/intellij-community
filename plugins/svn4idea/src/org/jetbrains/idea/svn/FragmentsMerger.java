package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Factory;
import com.intellij.util.Consumer;

public class FragmentsMerger<U, T extends Consumer<U>> {
  private T myData;
  private final Object myLock;
  private final Factory<T> myFactory;

  public FragmentsMerger(final Factory<T> factory) {
    myFactory = factory;
    myLock = new Object();
    myData = myFactory.create();
  }

  public void add(final U data) {
    synchronized (myLock) {
      // only T decides what to do with input, not the external code
      myData.consume(data);
    }
  }

  public T receive() {
    synchronized (myLock) {
      final T copy = myData;
      myData = myFactory.create();
      return copy;
    }
  }
}
