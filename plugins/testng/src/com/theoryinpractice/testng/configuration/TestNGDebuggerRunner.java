// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.JavaTestFrameworkDebuggerRunner;
import com.intellij.execution.configurations.RunProfile;
import org.jetbrains.annotations.NotNull;


public class TestNGDebuggerRunner extends JavaTestFrameworkDebuggerRunner {
  @Override
  protected boolean validForProfile(@NotNull RunProfile profile) {
    return profile instanceof TestNGConfiguration;
  }

  @Override
  protected @NotNull String getThreadName() {
    return "testng";
  }

  @Override
  public @NotNull String getRunnerId() {
    return "TestNGDebug";
  }
}
