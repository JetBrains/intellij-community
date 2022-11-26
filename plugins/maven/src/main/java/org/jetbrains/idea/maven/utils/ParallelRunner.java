// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

public class ParallelRunner {
  @FunctionalInterface
  public interface ThrowingConsumer<T> extends Consumer<T> {
    @Override
    default void accept(final T e) {
      try {
        accept0(e);
      }
      catch (Throwable ex) {
        sneakyThrow(ex);
      }
    }

    void accept0(T e) throws Throwable;
  }

  @NotNull
  private static <T> Consumer<T> toThrowingConsumer(@NotNull ThrowingConsumer<T> consumer) {
    return consumer;
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(@NotNull Throwable ex) throws E {
    throw (E)ex;
  }


  public static <T> void runInParallel(@NotNull Collection<T> collection, @NotNull Consumer<T> method) {
    collection.parallelStream().forEach(method);
  }

  @SuppressWarnings("RedundantThrows")
  public static <T, E extends Throwable> void runInParallelRethrow(@NotNull Collection<T> collection, @NotNull ThrowingConsumer<T> method)
    throws E {
    collection.parallelStream().forEach(toThrowingConsumer(item -> {
      method.accept(item);
    }));
  }

  @SuppressWarnings("unchecked")
  public static <T, E extends Throwable> void runInParallelRethrowWithAppExecutor(@NotNull Collection<T> collection, @NotNull ThrowingConsumer<T> method)
    throws E {
    var executor = AppExecutorUtil.getAppExecutorService();
    try {
      for (var item : collection) {
        var future = executor.submit(() -> method.accept(item));
        ConcurrencyUtil.manifestExceptionsIn(future);
      }
    } catch(RuntimeException e) {
      var cause = e.getCause();
      if (null != cause) {
        throw (E)cause;
      }
      throw e;
    }
  }


  public static <T> void runSequentially(@NotNull Collection<T> collection, @NotNull Consumer<T> method) {
    collection.forEach(method);
  }

  @SuppressWarnings("RedundantThrows")
  public static <T, E extends Throwable> void runSequentiallyRethrow(@NotNull Collection<T> collection, @NotNull ThrowingConsumer<T> method)
    throws E {
    collection.forEach(method);
  }
}
