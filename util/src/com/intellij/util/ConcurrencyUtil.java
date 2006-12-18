package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * @author cdr
 */
public class ConcurrencyUtil {
  /**
   * invokes and waits all tasks using threadPool, avoiding thread starvation on the way
   * @lookat http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html
   */
  public static void invokeAll(@NotNull Collection<Runnable> tasks, Executor executorService) throws Throwable {
    if (executorService == null) {
      for (Runnable task : tasks) {
        task.run();
      }
      return;
    }
    List<Future<Object>> futures = new ArrayList<Future<Object>>(tasks.size());
    boolean done = false;
    try {
      for (Runnable t : tasks) {
        FutureTask<Object> f = new FutureTask<Object>(t,null);
        futures.add(f);
        executorService.execute(f);
      }
      // force unstarted futures to execute using the current thread
      for (Future<Object> f : futures) ((FutureTask)f).run();
      for (Future<Object> f : futures) {
        if (!f.isDone()) {
          try {
            f.get();
          }
          catch (CancellationException ignore) {
          }
          catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
              throw cause;
            }
          }
        }
      }
      done = true;
    }
    finally {
      if (!done) {
        for (Future<Object> f : futures) {
          f.cancel(false);
        }
      }
    }
  }
}
