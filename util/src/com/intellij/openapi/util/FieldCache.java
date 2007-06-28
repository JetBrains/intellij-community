package com.intellij.openapi.util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class FieldCache<T, Owner,AccessorParameter,Parameter> {

  private ReentrantReadWriteLock ourLock = new ReentrantReadWriteLock();
  private ReentrantReadWriteLock.ReadLock r;
  private ReentrantReadWriteLock.WriteLock w;


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