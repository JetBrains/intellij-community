// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public enum TestEnv {

  WINDOWS(() -> SystemInfo.isWindows), LINUX(() -> SystemInfo.isLinux), MAC(() -> SystemInfo.isMac);

  @NotNull
  private final Supplier<Boolean> myThisOs;

  TestEnv(@NotNull final Supplier<Boolean> isThisOs) {
    myThisOs = isThisOs;
  }

  public boolean isThisOs() {
    return myThisOs.get();
  }
}
