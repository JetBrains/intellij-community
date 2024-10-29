// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.actions.context;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.actions.BaseTaskAction;
import com.intellij.tasks.context.WorkingContextManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class SaveContextAction extends BaseTaskAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = getProject(e);
    saveContext(project);
  }

  public static void saveContext(Project project) {

    String initial = null;
    Editor textEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (textEditor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(textEditor.getDocument());
      if (file != null) {
        initial = file.getName();
      }
    }
    String comment = Messages.showInputDialog(project, TaskBundle.message("task.save.context.action.message"),
                                              TaskBundle.message("task.save.context.action.name"), null, initial, null);
    if (comment != null) {
      WorkingContextManager.getInstance(project).saveContext(null, StringUtil.isEmpty(comment) ? null : comment);
    }
  }
}
