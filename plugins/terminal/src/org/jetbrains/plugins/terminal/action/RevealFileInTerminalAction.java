// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalView;

/**
 * An action that activates the terminal window for file, selected by user.
 */
public class RevealFileInTerminalAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isAvailable(e));
  }

  private static boolean isAvailable(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return project != null && !LightEdit.owns(project) && getSelectedFile(e) != null &&
           (!ActionPlaces.isPopupPlace(e.getPlace()) || editor == null || !editor.getSelectionModel().hasSelection());
  }

  @Nullable
  private static VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
    return RevealFileAction.findLocalFile(e.getData(CommonDataKeys.VIRTUAL_FILE));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile selectedFile = getSelectedFile(e);
    if (project == null || selectedFile == null) {
      return;
    }
    TerminalView.getInstance(project).openTerminalIn(selectedFile);
  }
}
