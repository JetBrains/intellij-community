package org.jetbrains.idea.svn;

public class ReentranceDefence {
  protected final ThreadLocal<Boolean> myThreadLocal;

  protected ReentranceDefence() {
    myThreadLocal = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return Boolean.TRUE;
      }
    };
  }

  public static <T> T executeReentrant(final ReentranceDefence defence, final MyControlled<T> controlled) {
    if (defence.isInside()) {
      return controlled.executeMeSimple();
    }
    return controlled.executeMe();
  }

  public boolean isInside() {
    return ! Boolean.TRUE.equals(myThreadLocal.get());
  }

  public boolean isOutside() {
    return ! isInside();
  }

  public void executeOtherDefended(final Runnable runnable) {
    try {
      myThreadLocal.set(Boolean.FALSE);
      runnable.run();
    } finally {
      myThreadLocal.set(Boolean.TRUE);
    }
  }

  public interface MyControlled<T> {
    T executeMe();
    T executeMeSimple();
  }
}
