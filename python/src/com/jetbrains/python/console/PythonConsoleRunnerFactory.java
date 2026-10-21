// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated extend {@link PyConsoleRunnerFactoryAsync} and implement its suspending methods.
 * <p>
 * This class only holds the extension point. It has no methods to build a runner.
 * {@link PyConsoleRunnerFactoryAsync#getInstance()} ignores a factory that extends only this class.
 */
@Deprecated
public abstract class PythonConsoleRunnerFactory {
  private static final ExtensionPointName<PythonConsoleRunnerFactory> EP_NAME =
    ExtensionPointName.create("com.jetbrains.python.console.runnerFactory");

  static @NotNull ExtensionPointName<PythonConsoleRunnerFactory> getEpName() {
    return EP_NAME;
  }
}
