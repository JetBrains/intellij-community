// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class TerminalPasteAction extends DumbAwareAction {

  public TerminalPasteAction() {
    getTemplatePresentation().setEnabled(false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // should never be called normally
    // handled by com.intellij.terminal.JBTerminalSystemSettingsProviderBase.getPasteActionPresentation
  }
}
