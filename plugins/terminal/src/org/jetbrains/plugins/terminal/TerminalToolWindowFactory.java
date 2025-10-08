// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

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

    var toolWindowEx = (ToolWindowEx)toolWindow;
    TerminalToolWindowManager terminalToolWindowManager = TerminalToolWindowManager.getInstance(project);
    terminalToolWindowManager.initToolWindow(toolWindowEx);
    TerminalToolWindowInitializer.performInitialization(toolWindowEx);

    boolean useReworkedTerminal = ExperimentalUI.isNewUI() &&
                                  TerminalOptionsProvider.getInstance().getTerminalEngine() == TerminalEngine.REWORKED;
    // Reworked Terminal tabs are restored in TerminalToolWindowInitializer.
    // If it is the frontend, do not restore classic tabs there, they are restored on the backend and then synchronized.
    if (!useReworkedTerminal && !PlatformUtils.isJetBrainsClient()) {
      TerminalArrangementManager terminalArrangementManager = TerminalArrangementManager.getInstance(project);
      terminalToolWindowManager.restoreTabsLocal(terminalArrangementManager.getArrangementState());
      // Allow saving tabs after the tabs are restored.
      terminalArrangementManager.setToolWindow(toolWindow);
    }
  }
}
