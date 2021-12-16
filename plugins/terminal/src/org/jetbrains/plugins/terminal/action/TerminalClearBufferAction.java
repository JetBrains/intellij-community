// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.actions.TerminalBaseContextAction;
import org.jetbrains.annotations.NotNull;

/**
 * @see JBTerminalSystemSettingsProviderBase#getClearBufferActionPresentation
 */
public class TerminalClearBufferAction extends TerminalBaseContextAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    JBTerminalWidget terminalWidget = getTerminalWidget(e);
    if (terminalWidget != null) {
      terminalWidget.getTerminalPanel().clearBuffer();
    }
  }
}
