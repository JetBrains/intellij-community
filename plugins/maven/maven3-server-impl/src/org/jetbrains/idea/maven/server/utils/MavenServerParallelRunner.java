// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.utils;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    }
    else {
      runSequentially(collection, method);
    }
  }

  public static <T, R, E extends Exception> List<R> executeSequentially(@NotNull Collection<T> collection,
                                                                        @NotNull CheckedFunction<T, R, E> method) throws E {
    List<R> result = new ArrayList<>();

    for (T item : collection) {
      result.add(method.apply(item));
    }

    return result;
  }

  public static <T, R, E extends Exception> List<R> executeInParallel(@NotNull Collection<T> collection,
                                                                      @NotNull CheckedFunction<T, R, E> method) throws E {
    Set<RuntimeException> runtimeExceptions = ConcurrentHashMap.newKeySet();
    Set<E> checkedExceptions = ConcurrentHashMap.newKeySet();

    List<R> result = collection.parallelStream().map(item -> {
        try {
          return new Pair<>(true, method.apply(item));
        }
        catch (RuntimeException ex) {
          runtimeExceptions.add(ex);
        }
        catch (Exception ex) {
          checkedExceptions.add((E)ex);
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

    if (!checkedExceptions.isEmpty()) {
      throw checkedExceptions.stream().reduce((ex1, ex2) -> {
        ex1.addSuppressed(ex2);
        return ex1;
      }).get();
    }

    return result;
  }

  public static <T, R, E extends Exception> List<R> execute(boolean inParallel,
                                                            @NotNull Collection<T> collection,
                                                            @NotNull CheckedFunction<T, R, E> method) throws E {
    return inParallel ? executeInParallel(collection, method) : executeSequentially(collection, method);
  }

  @FunctionalInterface
  public interface CheckedFunction<T, R, E extends Exception> {
    R apply(T t) throws E;
  }
}
