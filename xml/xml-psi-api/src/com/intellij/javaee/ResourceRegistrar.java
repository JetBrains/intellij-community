// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Avdeev
 * @see StandardResourceProvider
 */
public interface ResourceRegistrar {
  void addStdResource(@NonNls String resource, @NonNls String fileName);

  void addStdResource(@NonNls String resource, @NonNls String fileName, Class<?> klass);

  void addStdResource(@NonNls String resource, @NonNls String version, @NonNls String fileName, Class<?> klass);

  void addIgnoredResource(@NonNls String url);
}
