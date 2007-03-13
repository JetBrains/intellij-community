package com.intellij.openapi.util;

public abstract class SimpleFieldCache<T, Owner> extends FieldCache<T,Owner,Object, Object>{
  public final T get(Owner owner) {
    return get(null, owner, null);
  }

  protected final T compute(Owner owner, Object p) {
    return compute(owner);
  }

  protected final T getValue(Owner owner, Object p) {
    return getValue(owner);
  }

  protected final void putValue(T t, Owner owner, Object p) {
    putValue(t, owner);
  }

  protected abstract T compute(Owner owner);
  protected abstract T getValue(Owner owner);
  protected abstract void putValue(T t, Owner owner);
}