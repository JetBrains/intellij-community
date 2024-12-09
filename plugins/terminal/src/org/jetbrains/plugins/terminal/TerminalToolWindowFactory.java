// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementManager;

public final class TerminalToolWindowFactory implements ToolWindowFactory, DumbAware {
  @NonNls public static final String TOOL_WINDOW_ID = "Terminal";

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    TerminalToolWindowManager terminalToolWindowManager = TerminalToolWindowManager.getInstance(project);
    terminalToolWindowManager.initToolWindow((ToolWindowEx)toolWindow);

    ActionGroup toolWindowActions = (ActionGroup)ActionManager.getInstance().getAction("Terminal.ToolWindowActions");
    toolWindow.setAdditionalGearActions(toolWindowActions);

    TerminalArrangementManager terminalArrangementManager = TerminalArrangementManager.getInstance(project);
    terminalToolWindowManager.restoreTabs(terminalArrangementManager.getArrangementState());
    // allow to save tabs after the tabs are restored
    terminalArrangementManager.setToolWindow(toolWindow);
  }
}
