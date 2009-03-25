package org.jetbrains.idea.svn;

public abstract class ThreadLocalDefendedInvoker<T> {
  protected final ThreadLocal<Boolean> myThreadLocal;

  protected ThreadLocalDefendedInvoker() {
    myThreadLocal = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return Boolean.TRUE;
      }
    };
  }

  protected abstract T execute();

  public boolean isInside() {
    return ! Boolean.TRUE.equals(myThreadLocal.get());
  }

  public boolean isOutside() {
    return ! isInside();
  }

  public T executeDefended() {
    try {
      myThreadLocal.set(Boolean.FALSE);
      return execute();
    } finally {
      myThreadLocal.set(Boolean.TRUE);
    }
  }
}
