// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.python.console;

import com.google.common.collect.Lists;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.run.PythonTracebackFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author traff
 */
public class PyDebugConsoleBuilder extends TextConsoleBuilder {
  private final Project myProject;
  private final ArrayList<Filter> myFilters = Lists.newArrayList();
  private final Sdk mySdk;

  public PyDebugConsoleBuilder(final Project project, @Nullable Sdk sdk) {
    myProject = project;
    mySdk = sdk;
  }

  @Override
  public ConsoleView getConsole() {
    final ConsoleView consoleView = createConsole();
    for (final Filter filter : myFilters) {
      consoleView.addMessageFilter(filter);
    }
    return consoleView;
  }

  protected  ConsoleView createConsole() {
    PythonDebugLanguageConsoleView consoleView = new PythonDebugLanguageConsoleView(myProject, mySdk);
    consoleView.addMessageFilter(new PythonTracebackFilter(myProject));
    return consoleView;
  }

  @Override
  public void addFilter(@NotNull final Filter filter) {
    myFilters.add(filter);
  }

  @Override
  public void setViewer(boolean isViewer) {
  }

}
