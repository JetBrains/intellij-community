package com.intellij.openapi.util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class UserDataCache<T, Owner extends UserDataHolder> {
  private final Key<T> myKey;

  private ReentrantReadWriteLock ourLock = new ReentrantReadWriteLock();
  private ReentrantReadWriteLock.ReadLock r;
  private ReentrantReadWriteLock.WriteLock w;


  protected UserDataCache(final Key<T> key) {
    myKey = key;
    r = ourLock.readLock();
    w = ourLock.writeLock();
  }

  public final T get(Owner owner) {
    assert owner instanceof UserDataHolderBase;

    T result;

    r.lock();

    result = owner.getUserData(myKey);

    if (result == null) {
      r.unlock();
      w.lock();

      try {
        result = owner.getUserData(myKey);
        if (result == null) {
          result = compute(owner);
          owner.putUserData(myKey, result);
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

  protected abstract T compute(Owner owner);
}
