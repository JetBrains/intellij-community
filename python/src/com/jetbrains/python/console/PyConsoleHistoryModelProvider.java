// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public final class PyConsoleHistoryModelProvider implements ConsoleHistoryModelProvider {
  @Override
  public @Nullable ConsoleHistoryModel createModel(@NotNull String persistenceId, @NotNull LanguageConsoleView consoleView) {
    return consoleView instanceof PythonConsoleView ? PrefixHistoryModelKt.createModel(persistenceId, consoleView) : null;
  }
}
