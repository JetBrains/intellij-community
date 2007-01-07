/*
 * @author max
 */
package com.intellij.util.containers;

public class LimitedPool<T> {
  private int capacity;
  private final ObjectFactory<T> factory;
  private Object[] storage;
  private int index = 0;

  public LimitedPool(final int capacity, ObjectFactory<T> factory) {
    this.capacity = capacity;
    this.factory = factory;
    storage = new Object[capacity];
  }

  public interface ObjectFactory<T> {
    T create();
    void cleanup(T t);
  }

  public T alloc() {
    if (index == 0) return factory.create();
    //noinspection unchecked
    return (T)storage[--index];
  }

  public void recycle(T t) {
    factory.cleanup(t);

    if (index >= capacity) return;
    storage[index++] = t;
  }
}