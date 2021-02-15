// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.embedder;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExecutorManager {
  private static final ExecutorService myExecutor = new ThreadPoolExecutor(3, Integer.MAX_VALUE, 30 * 60L, TimeUnit.SECONDS,
                                                                           new SynchronousQueue<Runnable>(),new ThreadFactory() {
      final AtomicInteger num = new AtomicInteger();
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, "Maven Embedder "+num.getAndIncrement());
      }
    });

  @NotNull
  public static <T> Future<T> execute(@NotNull Callable<T> r) {
    return myExecutor.submit(r);
  }
}
