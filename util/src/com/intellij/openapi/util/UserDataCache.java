package com.intellij.openapi.util;

public abstract class UserDataCache<T, Owner extends UserDataHolder, S> extends FieldCache<T,Owner,Key<T>,S> {
  protected final T getValue(final Owner owner, final Key<T> key) {
    return owner.getUserData(key);
  }

  protected final void putValue(final T t, final Owner owner, final Key<T> key) {
    owner.putUserData(key, t);
  }
}
