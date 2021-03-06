// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalView;

import java.util.Objects;

public class GotoSplitTerminalActionBase extends TerminalSessionContextMenuActionBase implements DumbAware {
  private final boolean myForward;

  private GotoSplitTerminalActionBase(boolean forward) {
    myForward = forward;
    getTemplatePresentation().setText(() -> {
      return JBTerminalSystemSettingsProviderBase.getGotoNextSplitTerminalActionText(forward);
    });
  }

  @Override
  public void updateInTerminalToolWindow(@NotNull AnActionEvent e, @NotNull Project project, @NotNull Content content) {
    JBTerminalWidget terminalWidget = TerminalView.getWidgetByContent(content);
    Presentation presentation = e.getPresentation();
    if (terminalWidget == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    presentation.setEnabledAndVisible(TerminalView.getInstance(project).isSplitTerminal(terminalWidget));
  }

  @Override
  public void actionPerformedInTerminalToolWindow(@NotNull AnActionEvent e, @NotNull Project project, @NotNull Content content) {
    JBTerminalWidget terminalWidget = TerminalSplitActionBase.getContextTerminal(e, content);
    if (terminalWidget != null) {
      TerminalView terminalView = TerminalView.getInstance(project);
      terminalView.gotoNextSplitTerminal(terminalWidget, myForward);
    }
  }

  public static final class Next extends GotoSplitTerminalActionBase {
    private Next() {
      super(true);
    }
  }

  public static final class Prev extends GotoSplitTerminalActionBase {
    private Prev() {
      super(false);
    }
  }
}
