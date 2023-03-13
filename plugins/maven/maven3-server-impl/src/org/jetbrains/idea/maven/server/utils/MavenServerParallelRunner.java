// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public final class MavenServerParallelRunner {
  public static <T> void runInParallel(@NotNull Collection<T> collection, @NotNull Consumer<T> method) {
    collection.parallelStream().forEach(method);
  }

  public static <T> void runSequentially(@NotNull Collection<T> collection, @NotNull Consumer<T> method) {
    collection.forEach(method);
  }

  public static <T> void run(boolean runInParallel, @NotNull Collection<T> collection, @NotNull Consumer<T> method) {
    if (runInParallel) {
      runInParallel(collection, method);
    } else {
      runSequentially(collection, method);
    }
  }

  public static <T, R> Set<R> executeInParallel(@NotNull Collection<T> collection, @NotNull Function<T, R> method) {
    Set<R> result = ConcurrentHashMap.newKeySet();
    collection.parallelStream().flatMap(item -> {
      try {
        result.add(method.apply(item));
        return null;
      }
      catch (RuntimeException ex) {
        return Stream.of(ex);
      }
    }).reduce((ex1, ex2) -> {
      ex1.addSuppressed(ex2);
      return ex1;
    }).ifPresent(ex -> {
      throw ex;
    });
    return result;
  }
}
