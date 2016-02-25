/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.debugger.PyRunCythonExtensionsFilter;
import com.jetbrains.python.run.PythonTracebackFilter;
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
    consoleView.addMessageFilter(new PyRunCythonExtensionsFilter(myProject));
    return consoleView;
  }

  public void addFilter(final Filter filter) {
    myFilters.add(filter);
  }

  @Override
  public void setViewer(boolean isViewer) {
  }

}
