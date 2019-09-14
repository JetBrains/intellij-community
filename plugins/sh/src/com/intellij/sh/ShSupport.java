// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ShSupport {
  static ShSupport getInstance() { return ServiceManager.getService(ShSupport.class); }

  boolean isExternalFormatterEnabled();

  boolean isRenameEnabled();

  @Nullable
  RunProfileState createRunProfileState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment);

  class Impl implements ShSupport {
    @Override
    public boolean isExternalFormatterEnabled() { return true; }

    @Override
    public boolean isRenameEnabled() { return true; }

    @Nullable
    @Override
    public RunProfileState createRunProfileState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
      return null;
    }
  }
}