// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PythonConsoleRunnerFactory {
  @NotNull
  public static PythonConsoleRunnerFactory getInstance() {
    return ApplicationManager.getApplication().getService(PythonConsoleRunnerFactory.class);
  }

  @NotNull
  public abstract PydevConsoleRunner createConsoleRunner(@NotNull Project project, @Nullable Module contextModule);

  @NotNull
  public abstract PydevConsoleRunner createConsoleRunnerWithFile(@NotNull Project project,
                                                                 @NotNull PythonRunConfiguration config);
}
