// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @see StandardResourceProvider
 */
public interface ResourceRegistrar {
  void addStdResource(@NotNull @NonNls String resource, @NonNls String fileName);

  void addStdResource(@NotNull @NonNls String resource, @NonNls String fileName, Class<?> klass);

  void addStdResource(@NotNull @NonNls String resource, @NonNls String version, @NonNls String fileName, Class<?> klass);

  void addIgnoredResource(@NotNull @NonNls String url);
}
