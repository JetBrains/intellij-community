// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

public class MavenServerParallelRunner {
  public static <T> void runInParallel(@NotNull Collection<T> collection, @NotNull Consumer<T> method) {
    collection.parallelStream().forEach(method);
  }

  public static <T> void runSequentially(@NotNull Collection<T> collection, @NotNull Consumer<T> method) {
    collection.forEach(method);
  }
}
