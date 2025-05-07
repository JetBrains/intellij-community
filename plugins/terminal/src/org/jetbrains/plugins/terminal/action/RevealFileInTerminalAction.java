// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalEngine;
import org.jetbrains.plugins.terminal.TerminalOptionsProvider;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

/**
 * An action that activates the terminal window for file, selected by user.
 */
public final class RevealFileInTerminalAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isAvailable(e));
  }

  private static boolean isAvailable(@NotNull AnActionEvent e) {
    if (TerminalOptionsProvider.getInstance().getTerminalEngine() == TerminalEngine.REWORKED) {
      return false;
    }

    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return project != null && !LightEdit.owns(project) && getSelectedFile(e) != null &&
           (!e.isFromContextMenu() || editor == null || !editor.getSelectionModel().hasSelection());
  }

  private static @Nullable VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
    return RevealFileAction.findLocalFile(e.getData(CommonDataKeys.VIRTUAL_FILE));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile selectedFile = getSelectedFile(e);
    if (project == null || selectedFile == null) {
      return;
    }
    TerminalToolWindowManager.getInstance(project).openTerminalIn(selectedFile);
  }
}
