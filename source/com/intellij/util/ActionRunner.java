/**
 * @author cdr
 */
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Computable;

import javax.swing.*;


public abstract class ActionRunner {
  public static  void runInsideWriteAction(final InterruptibleRunnable runnable) throws Exception {
    final Exception[] exception = new Exception[1];
    Runnable swingRunnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              runnable.run();
            }
            catch (Exception e) {
              exception[0] = e;
            }
          }
        });
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      swingRunnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(swingRunnable, ModalityState.NON_MMODAL);
    }
    Exception e = exception[0];
    if (e != null) {
      if (e instanceof RuntimeException) throw (RuntimeException)e;
      throw new Exception(e);
    }
  }
  //public static <E extends Throwable> void runInsideWriteAction(final InterruptibleRunnable<E> runnable) throws E {
  //  runInsideWriteAction(new InterruptibleRunnableWithResult<E,Object>(){
  //    public Object run() throws E {
  //      runnable.run();
  //      return null;
  //    }
  //  });
  //}
  public static <T> T runInsideWriteAction(final InterruptibleRunnableWithResult<T> runnable) throws Exception {
    final Throwable[] exception = new Throwable[]{null};
    final T[] result = (T[])new Object[1];
    Runnable swingRunnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              result[0] = runnable.run();
            }
            catch (Exception e) {
              exception[0] = e;
            }
          }
        });
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      swingRunnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(swingRunnable, ModalityState.NON_MMODAL);
    }
    Throwable e = exception[0];
    if (e != null) {
      if (e instanceof RuntimeException) throw (RuntimeException)e;
      throw new Exception(e);
    }
    return result[0];
  }

  public static void runInsideReadAction(final InterruptibleRunnable runnable) throws Exception {
    Throwable exception = ApplicationManager.getApplication().runReadAction(new Computable<Throwable>() {
      public Throwable compute() {
        try {
          runnable.run();
          return null;
        }
        catch (Throwable e) {
          return e;
        }
      }
    });
    if (exception != null) {
      if (exception instanceof RuntimeException) {
        throw (RuntimeException)exception;
      }
      throw new Exception(exception);
    }
  }

  public static interface InterruptibleRunnable {
    void run() throws Exception;
  }
  public static interface InterruptibleRunnableWithResult <T> {
    T run() throws Exception;
  }
}