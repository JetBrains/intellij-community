// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains;

import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * @author traff
 */
public enum TestEnv {

  WINDOWS(() -> SystemInfoRt.isWindows), LINUX(() -> SystemInfoRt.isLinux), MAC(() -> SystemInfoRt.isMac);

  @NotNull
  private final Supplier<Boolean> myThisOs;

  TestEnv(@NotNull final Supplier<Boolean> isThisOs) {
    myThisOs = isThisOs;
  }

  public boolean isThisOs() {
    return myThisOs.get();
  }
}
