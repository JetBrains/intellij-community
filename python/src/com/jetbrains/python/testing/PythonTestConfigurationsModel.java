// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.python.testing;

/**
 * @deprecated Use {@link PyUnitTestFactory#id} instead
 */
@Deprecated(forRemoval = true)
public final class PythonTestConfigurationsModel {
  private PythonTestConfigurationsModel() {
  }

  /**
   * @deprecated Use {@link PyUnitTestFactory#id} instead
   */
  @Deprecated(forRemoval = true)
  public static final String PYTHONS_UNITTEST_NAME = PyUnitTestFactory.id;
}
