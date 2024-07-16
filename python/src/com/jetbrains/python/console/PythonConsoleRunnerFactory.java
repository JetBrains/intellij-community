// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PythonConsoleRunnerFactory {
  private static final ExtensionPointName<PythonConsoleRunnerFactory> EP_NAME =
    ExtensionPointName.create("com.jetbrains.python.console.runnerFactory");

  public static @NotNull PythonConsoleRunnerFactory getInstance() {
    return EP_NAME.getExtensionList().get(0);
  }

  public abstract @NotNull PydevConsoleRunner createConsoleRunner(@NotNull Project project, @Nullable Module contextModule);

  public abstract @NotNull PydevConsoleRunner createConsoleRunnerWithFile(@NotNull Project project,
                                                                          @NotNull PythonRunConfiguration config);
}
