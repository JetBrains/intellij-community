package org.jetbrains.idea.svn.integrate;

public class CheckSamePattern<T> {
  private boolean mySame;
  private T mySameValue;

  public CheckSamePattern() {
    mySameValue = null;
    mySame = true;
  }

  public void iterate(final T t) {
    if (t == null) {
      mySame = false;
      return;
    }
    if (mySameValue == null) {
      mySameValue = t;
      return;
    }
    mySame &= mySameValue.equals(t);
  }

  public boolean isSame() {
    return mySame;
  }

  public T getSameValue() {
    return mySameValue;
  }
}
