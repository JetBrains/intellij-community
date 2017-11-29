/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.intellij.execution.console.ConsoleHistoryModel;
import com.intellij.execution.console.ConsoleHistoryModelProvider;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.console.PrefixHistoryModelKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yuli Fiterman
 */
public class PyConsoleHistoryModelProvider implements ConsoleHistoryModelProvider {
  @Nullable
  @Override
  public ConsoleHistoryModel createModel(@NotNull String persistenceId, @NotNull LanguageConsoleView consoleView) {
    return consoleView instanceof PythonConsoleView ? PrefixHistoryModelKt.createModel(persistenceId, consoleView) : null;
  }
}
