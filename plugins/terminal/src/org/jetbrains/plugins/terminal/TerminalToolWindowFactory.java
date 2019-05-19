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
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementManager;

/**
 * @author traff
 */
public class TerminalToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String TOOL_WINDOW_ID = "Terminal";
  
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    TerminalView terminalView = TerminalView.getInstance(project);
    terminalView.initToolWindow(toolWindow);
    terminalView.restoreTabs(TerminalArrangementManager.getInstance(project).getArrangementState());
    // allow to save tabs after the tabs are restored
    TerminalArrangementManager.getInstance(project).setToolWindow(toolWindow);
  }
}
