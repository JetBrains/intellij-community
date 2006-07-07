package com.intellij.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SpinAllocator can be used for allocating short-live automatic objects of type T.
 * Avoiding reenterable allocations, MAX_SIMULTANEOUS_ALLOCATIONS are concurrently possible.
 */
public class SpinAllocator<T> {

  public static final int MAX_SIMULTANEOUS_ALLOCATIONS = 64;

  public interface ICreator<T> {
    T createInstance();
  }

  public interface IDisposer<T> {
    void disposeInstance(T instance);
  }

  private AtomicBoolean[] myEmployed = new AtomicBoolean[MAX_SIMULTANEOUS_ALLOCATIONS];
  private Object[] myObjects = new Object[MAX_SIMULTANEOUS_ALLOCATIONS];
  protected final ICreator<T> myCreator;
  protected final IDisposer<T> myDisposer;

  public SpinAllocator(ICreator<T> creator, IDisposer<T> disposer) {
    myCreator = creator;
    myDisposer = disposer;
    for (int i = 0; i < MAX_SIMULTANEOUS_ALLOCATIONS; ++i) {
      myEmployed[i] = new AtomicBoolean(false);
    }
  }

  public T alloc() {
    for (int i = 0; i < MAX_SIMULTANEOUS_ALLOCATIONS; ++i) {
      if (!myEmployed[i].getAndSet(true)) {
        T result = (T)myObjects[i];
        if (result == null) {
          myObjects[i] = result = myCreator.createInstance();
        }
        return result;
      }
    }
    throw new RuntimeException("SpinAllocator has exhausted! Too many threads or you're going to get StackOverflow.");
  }

  public void dispose(T instance) {
    for (int i = 0; i < MAX_SIMULTANEOUS_ALLOCATIONS; ++i) {
      if (myObjects[i] == instance) {
        if (!myEmployed[i].get()) {
          throw new RuntimeException("Instance is already disposed.");
        }
        myDisposer.disposeInstance(instance);
        myEmployed[i].set(false);
        return;
      }
    }
    throw new RuntimeException("Attempt to dispose non-allocated instance.");
  }
}
