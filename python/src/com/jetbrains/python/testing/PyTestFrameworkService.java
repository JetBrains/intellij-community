// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link PyTestsSharedKt#getFactoryById(String)}
 */
@Deprecated
public final class PyTestFrameworkService {
  private PyTestFrameworkService() {

  }

  /**
   * @deprecated use {@link PyTestsSharedKt#getFactoryById(String)}
   */

  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Deprecated
  @NotNull
  public static @Nls String getSdkReadableNameByFramework(@NotNull final String frameworkId) {
    var factory = PyTestsSharedKt.getFactoryById(frameworkId);
    if (factory == null) {
      throw new IllegalArgumentException("Unknown framework " + frameworkId);
    }
    return factory.getName();
  }
}
