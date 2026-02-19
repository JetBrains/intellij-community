// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service interface for resolving code owners of benchmark test classes.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 * If no implementation is on the classpath (e.g. local runs), owner resolution is silently skipped.
 */
public interface BenchmarkCodeOwnerResolver {
  @Nullable
  String getOwnerGroupName(@NotNull Class<?> testClass);
}
