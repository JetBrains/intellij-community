// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ParallelRunnerForServer {
  private static <T, R> List<R> executeSequentially(@NotNull Collection<T> collection,
                                                    @NotNull Function<T, R> method) {
    List<R> result = new ArrayList<>();

    for (T item : collection) {
      result.add(method.apply(item));
    }

    return result;
  }

  private static <T, R> List<R> executeInParallel(@NotNull Collection<T> collection,
                                                  @NotNull Function<T, R> method) {
    Set<RuntimeException> runtimeExceptions = ConcurrentHashMap.newKeySet();

    List<R> result = collection.parallelStream().map(item -> {
        try {
          return new Pair<>(true, method.apply(item));
        }
        catch (RuntimeException ex) {
          runtimeExceptions.add(ex);
        }
        return new Pair<Boolean, R>(false, null);
      })
      .filter(pair -> pair.getFirst())
      .map(pair -> pair.second)
      .collect(Collectors.toList());

    if (!runtimeExceptions.isEmpty()) {
      throw runtimeExceptions.stream().reduce((ex1, ex2) -> {
        ex1.addSuppressed(ex2);
        return ex1;
      }).get();
    }

    return result;
  }

  public static <T, R> List<R> execute(boolean inParallel,
                                       @NotNull Collection<T> collection,
                                       @NotNull Function<T, R> method) {
    return inParallel ? executeInParallel(collection, method) : executeSequentially(collection, method);
  }
}
