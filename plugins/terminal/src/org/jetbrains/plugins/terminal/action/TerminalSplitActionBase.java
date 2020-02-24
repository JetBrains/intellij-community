// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalView;

import javax.swing.*;
import java.util.Objects;

public class TerminalSplitActionBase extends TerminalSessionContextMenuActionBase implements DumbAware {
  private final boolean myVertically;

  private TerminalSplitActionBase(@NotNull String text, @Nullable Icon icon, boolean vertically) {
    getTemplatePresentation().setText(text);
    getTemplatePresentation().setIcon(icon);
    myVertically = vertically;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow, @Nullable Content content) {
    Project project = Objects.requireNonNull(e.getProject());
    JBTerminalWidget terminalWidget = TerminalView.getWidgetByContent(Objects.requireNonNull(content));
    if (terminalWidget != null) {
      TerminalView.getInstance(project).split(terminalWidget, myVertically);
    }
  }

  public static class Vertical extends TerminalSplitActionBase {
    private Vertical() {
      super(ActionsBundle.message("action.SplitVertically.text"), AllIcons.Actions.SplitVertically, true);
    }
  }

  public static class Horizontal extends TerminalSplitActionBase {
    private Horizontal() {
      super(ActionsBundle.message("action.SplitHorizontally.text"), AllIcons.Actions.SplitHorizontally, false);
    }
  }
}
