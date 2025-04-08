// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.client.ClientSessionsManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.arrangement.TerminalArrangementManager;

public final class TerminalToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final @NonNls String TOOL_WINDOW_ID = "Terminal";

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    TerminalToolWindowManager terminalToolWindowManager = TerminalToolWindowManager.getInstance(project);
    terminalToolWindowManager.initToolWindow((ToolWindowEx)toolWindow);

    ActionGroup toolWindowActions = (ActionGroup)ActionManager.getInstance().getAction("Terminal.ToolWindowActions");
    toolWindow.setAdditionalGearActions(toolWindowActions);
    if (ExperimentalUI.isNewUI() && TerminalOptionsProvider.getInstance().getTerminalEngine() == TerminalEngine.REWORKED) {
      // Restore from backend if Reworked Terminal (Gen2) is enabled.
      terminalToolWindowManager.restoreTabsFromBackend();
    }
    else {
      ClientAppSession clientAppSession = ClientSessionsManager.getAppSession(ClientId.getCurrent());
      //Do not restore tabs on client side, they are restored on backend and then synchronized
      if(clientAppSession != null) return;
      // Restore from local state otherwise.
      TerminalArrangementManager terminalArrangementManager = TerminalArrangementManager.getInstance(project);
      terminalToolWindowManager.restoreTabsLocal(terminalArrangementManager.getArrangementState());
      // Allow saving tabs after the tabs are restored.
      terminalArrangementManager.setToolWindow(toolWindow);
    }
  }
}
