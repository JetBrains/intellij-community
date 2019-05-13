/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  public abstract XDebugSession createSession(@NotNull PythonCommandLineState state,
                                              @NotNull ExecutionEnvironment environment)
    throws ExecutionException;

  @Contract("null -> null")
  @Nullable
  public static PyDebugSessionFactory findExtension(@Nullable Sdk sdk) {
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
