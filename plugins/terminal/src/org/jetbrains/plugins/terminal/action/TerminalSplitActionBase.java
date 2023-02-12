// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import javax.swing.*;
import java.util.function.Supplier;

public class TerminalSplitActionBase extends TerminalSessionContextMenuActionBase implements DumbAware {
  private final boolean myVertically;

  private TerminalSplitActionBase(@NotNull Supplier<String> text, @Nullable Icon icon, boolean vertically) {
    getTemplatePresentation().setText(text);
    getTemplatePresentation().setIcon(icon);
    myVertically = vertically;
  }

  @Override
  public void actionPerformedInTerminalToolWindow(@NotNull AnActionEvent e, @NotNull Project project, @NotNull Content content,
                                                  @NotNull TerminalWidget terminalWidget) {
    TerminalToolWindowManager.getInstance(project).split(terminalWidget, myVertically);
  }

  public static final class Vertical extends TerminalSplitActionBase {
    private Vertical() {
      super(ActionsBundle.messagePointer("action.SplitVertically.text"), AllIcons.Actions.SplitVertically, true);
    }
  }

  public static final class Horizontal extends TerminalSplitActionBase {
    private Horizontal() {
      super(ActionsBundle.messagePointer("action.SplitHorizontally.text"), AllIcons.Actions.SplitHorizontally, false);
    }
  }
}
