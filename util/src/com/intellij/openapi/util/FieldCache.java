package com.intellij.openapi.util;

import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;

public abstract class FieldCache<T, Owner,AccessorParameter,Parameter> {

  private JBReentrantReadWriteLock ourLock = LockFactory.createReadWriteLock();
  private JBLock r;
  private JBLock w;


  protected FieldCache() {
    r = ourLock.readLock();
    w = ourLock.writeLock();
  }

  public final T get(AccessorParameter a, Owner owner, Parameter p) {
    T result;

    r.lock();
    try {
      result = getValue(owner,a);
    } finally {
      r.unlock();
    }

    if (result == null) {
      w.lock();

      try {
        result = getValue(owner,a);
        if (result == null) {
          result = compute(owner, p);
          putValue(result, owner, a);
        }
      }
      finally {
        w.unlock();
      }
    }
    return result;
  }

  public final T getCached(AccessorParameter a, Owner owner) {
    r.lock();

    try {
      return getValue(owner, a);
    }
    finally {
      r.unlock();
    }
  }

  public final void clear(AccessorParameter a, Owner owner) {
    w.lock();
    try {
      putValue(null, owner, a);
    }
    finally{
      w.unlock();
    }
  }

  protected abstract T compute(Owner owner, Parameter p);
  protected abstract T getValue(Owner owner, AccessorParameter p);
  protected abstract void putValue(T t, Owner owner, AccessorParameter p);
}