// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalView;

import java.util.Objects;

public class GotoSplitTerminalActionBase extends TerminalSessionContextMenuActionBase implements DumbAware {
  private final boolean myForward;

  private GotoSplitTerminalActionBase(boolean forward) {
    myForward = forward;
    getTemplatePresentation().setText(JBTerminalSystemSettingsProviderBase.getGotoNextSplitTerminalActionText(forward));
  }

  @Override
  public void update(@NotNull AnActionEvent e, @NotNull ToolWindow activeToolWindow, @Nullable Content content) {
    JBTerminalWidget terminalWidget = TerminalView.getWidgetByContent(Objects.requireNonNull(content));
    if (terminalWidget == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Project project = Objects.requireNonNull(e.getProject());
    e.getPresentation().setEnabledAndVisible(TerminalView.getInstance(project).isSplitTerminal(terminalWidget));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow, @Nullable Content content) {
    JBTerminalWidget terminalWidget = TerminalSplitActionBase.getContextTerminal(e, Objects.requireNonNull(content));
    if (terminalWidget != null) {
      TerminalView terminalView = TerminalView.getInstance(Objects.requireNonNull(e.getProject()));
      terminalView.gotoNextSplitTerminal(terminalWidget, myForward);
    }
  }

  public static class Next extends GotoSplitTerminalActionBase {
    private Next() {
      super(true);
    }
  }

  public static class Prev extends GotoSplitTerminalActionBase {
    private Prev() {
      super(false);
    }
  }
}
