// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.idea.AppMode;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.PlatformUtils;
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
      // Fetch the tabs from the backend and restore them if Reworked Terminal (Gen2) is enabled.
      // If we are already on the backend, do nothing because tabs should be opened only on the frontend.
      if (!AppMode.isRemoteDevHost()) {
        terminalToolWindowManager.restoreTabsFromBackend();
      }
    }
    else {
      // Do not restore tabs on the client side, they are restored on the backend and then synchronized
      if (!PlatformUtils.isJetBrainsClient()) {
        // Restore from local state otherwise.
        TerminalArrangementManager terminalArrangementManager = TerminalArrangementManager.getInstance(project);
        terminalToolWindowManager.restoreTabsLocal(terminalArrangementManager.getArrangementState());
        // Allow saving tabs after the tabs are restored.
        terminalArrangementManager.setToolWindow(toolWindow);
      }
    }
  }
}
