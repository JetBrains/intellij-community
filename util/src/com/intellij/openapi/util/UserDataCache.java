package com.intellij.openapi.util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class UserDataCache<T, Owner extends UserDataHolder, S> {

  private ReentrantReadWriteLock ourLock = new ReentrantReadWriteLock();
  private ReentrantReadWriteLock.ReadLock r;
  private ReentrantReadWriteLock.WriteLock w;


  protected UserDataCache() {
    r = ourLock.readLock();
    w = ourLock.writeLock();
  }

  public final T get(Key<T> key,Owner owner, S s) {
    assert owner instanceof UserDataHolderBase;

    T result;

    r.lock();

    result = owner.getUserData(key);

    if (result == null) {
      r.unlock();
      w.lock();

      try {
        result = owner.getUserData(key);
        if (result == null) {
          result = compute(owner, s);
          owner.putUserData(key, result);
        }
      }
      finally {
        w.unlock();
      }
    }
    else {
      r.unlock();
    }

    return result;
  }

  public final T getCached(Key<T> key, Owner owner) {
    r.lock();

    try {
      return owner.getUserData(key);
    }
    finally {
      r.unlock();
    }
  }

  public final void clear(Key<T> key, Owner owner) {
    w.lock();
    try {
      owner.putUserData(key, null);
    }
    finally{
      w.unlock();
    }
  }

  protected abstract T compute(Owner owner, S s);
}
