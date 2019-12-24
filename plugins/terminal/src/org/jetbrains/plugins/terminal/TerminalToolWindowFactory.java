// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class TerminalToolWindowFactory implements ToolWindowFactory, DumbAware {
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
