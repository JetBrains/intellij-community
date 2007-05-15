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
    storage = new Object[10];
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

    ensureCapacity();
    storage[index++] = t;
  }

  private void ensureCapacity() {
    if (storage.length <= index + 1) {
      int newCapacity = Math.min(capacity, storage.length * 3 / 2);
      Object[] newStorage = new Object[newCapacity];
      System.arraycopy(storage, 0, newStorage, 0, storage.length);
      storage = newStorage;
    }
  }
}