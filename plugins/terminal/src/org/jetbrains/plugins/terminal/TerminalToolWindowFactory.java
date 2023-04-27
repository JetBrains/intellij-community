// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalCommandHandlerCustomizer.TerminalCommandHandlerOptions;
import org.jetbrains.plugins.terminal.action.TerminalAdvancedSettingToggleAction;
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
    TerminalCommandHandlerOptions options = new TerminalCommandHandlerOptions(project);
    toolWindow.setAdditionalGearActions(new DefaultActionGroup(
      new SmartCommandExecutionToggleAction(options),
      new TerminalAdvancedSettingToggleAction("terminal.use.1.0.line.spacing.for.alternative.screen.buffer"),
      new TerminalAdvancedSettingToggleAction("terminal.fill.character.background.including.line.spacing")
    ));

    TerminalArrangementManager terminalArrangementManager = TerminalArrangementManager.getInstance(project);
    terminalToolWindowManager.restoreTabs(terminalArrangementManager.getArrangementState());
    // allow to save tabs after the tabs are restored
    terminalArrangementManager.setToolWindow(toolWindow);
  }

  private static class SmartCommandExecutionToggleAction extends DumbAwareToggleAction {
    private final TerminalCommandHandlerOptions myOptions;

    private SmartCommandExecutionToggleAction(TerminalCommandHandlerOptions options) {
      //noinspection DialogTitleCapitalization
      super(TerminalBundle.message("settings.terminal.smart.command.handling"));
      myOptions = options;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myOptions.getEnabled();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myOptions.setEnabled(state);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
