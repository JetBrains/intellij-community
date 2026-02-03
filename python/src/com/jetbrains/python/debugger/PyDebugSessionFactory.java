// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.python.run.PythonCommandLineState;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Koshevoy
 */
public abstract class PyDebugSessionFactory {
  public static final ExtensionPointName<PyDebugSessionFactory> EP_NAME = ExtensionPointName.create("Pythonid.debugSessionFactory");

  protected abstract boolean appliesTo(@NotNull Sdk sdk);

  public abstract @NotNull XDebugSession createSession(@NotNull PythonCommandLineState state,
                                                       @NotNull ExecutionEnvironment environment)
    throws ExecutionException;

  @Contract("null -> null")
  public static @Nullable PyDebugSessionFactory findExtension(@Nullable Sdk sdk) {
    if (sdk == null) {
      return null;
    }
    for (PyDebugSessionFactory sessionCreator : EP_NAME.getExtensions()) {
      if (sessionCreator.appliesTo(sdk)) {
        return sessionCreator;
      }
    }
    return null;
  }
}
