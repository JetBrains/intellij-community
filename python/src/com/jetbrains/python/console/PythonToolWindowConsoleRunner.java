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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PythonToolWindowConsoleRunner extends PydevConsoleRunner {
  private ToolWindow myToolWindow;

  public PythonToolWindowConsoleRunner(@NotNull Project project,
                                       @NotNull Sdk sdk,
                                       @NotNull PyConsoleType consoleType,
                                       @Nullable String workingDir, Map<String, String> environmentVariables,
                                       @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                       String... statementsToExecute) {
    super(project, sdk, consoleType, workingDir, environmentVariables, settingsProvider, statementsToExecute);
  }

  @Override
  public void open() {
    getToolWindow().activate(new Runnable() {
      @Override
      public void run() {
      }
    }, true);
  }

  public ToolWindow getToolWindow() {
    if (myToolWindow == null) {
      myToolWindow = ToolWindowManager.getInstance(getProject()).getToolWindow(PythonConsoleToolWindowFactory.ID);
    }
    return myToolWindow;
  }

  @Override
  protected void showConsole(Executor defaultExecutor, @NotNull RunContentDescriptor contentDescriptor) {
    PythonConsoleToolWindow consoleToolWindow = PythonConsoleToolWindow.getInstance(getProject());
    consoleToolWindow.init(getToolWindow(), contentDescriptor);
  }

  @Override
  protected void clearContent(RunContentDescriptor descriptor) {
    Content content = getToolWindow().getContentManager().findContent(descriptor.getDisplayName());
    assert content != null;
    getToolWindow().getContentManager().removeContent(content, true);
  }

  @Override
  protected List<String> getActiveConsoleNames(final String consoleTitle) {
    return FluentIterable.from(
      Lists.newArrayList(PythonConsoleToolWindow.getInstance(getProject()).getToolWindow().getContentManager().getContents())).transform(
      new Function<Content, String>() {
        @Override
        public String apply(Content input) {
          return input.getDisplayName();
        }
      }).filter(new Predicate<String>() {
      @Override
      public boolean apply(String input) {
        return input.contains(consoleTitle);
      }
    }).toList();
  }
}
