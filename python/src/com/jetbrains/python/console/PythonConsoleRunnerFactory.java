// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated extend {@link PyConsoleRunnerFactoryAsync} and implement its suspending methods.
 * <p>
 * Both methods here block, and building a runner waits for the project model. A synchronous read answers
 * {@code null} for a module that has an interpreter while the SDK table still loads, so a console started right
 * after a project opens picks the wrong interpreter or none.
 * <p>
 * Nothing here changed shape, because factories outside this repository extend this class and call it.
 */
@Deprecated
public abstract class PythonConsoleRunnerFactory {
  private static final ExtensionPointName<PythonConsoleRunnerFactory> EP_NAME =
    ExtensionPointName.create("com.jetbrains.python.console.runnerFactory");

  static @NotNull ExtensionPointName<PythonConsoleRunnerFactory> getEpName() {
    return EP_NAME;
  }

  /**
   * @deprecated call {@link PyConsoleRunnerFactoryAsync#getInstance()}.
   */
  @Deprecated
  public static @NotNull PythonConsoleRunnerFactory getInstance() {
    return EP_NAME.getExtensionList().get(0);
  }

  /**
   * @deprecated implement {@link PyConsoleRunnerFactoryAsync#createConsoleRunnerAsync}.
   */
  @Deprecated
  public abstract @NotNull PydevConsoleRunner createConsoleRunner(@NotNull Project project, @Nullable Module contextModule);

  /**
   * @deprecated implement {@link PyConsoleRunnerFactoryAsync#createConsoleRunnerWithFileAsync}.
   */
  @Deprecated
  public abstract @NotNull PydevConsoleRunner createConsoleRunnerWithFile(@NotNull Project project,
                                                                          @NotNull PythonRunConfiguration config);
}
