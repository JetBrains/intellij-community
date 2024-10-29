// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.actions.context;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.actions.BaseTaskAction;
import com.intellij.tasks.context.WorkingContextManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class ClearContextAction extends BaseTaskAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = getProject(e);
    GlobalUndoableAction action = new GlobalUndoableAction() {
      @Override
      public void undo() throws UnexpectedUndoException {

      }

      @Override
      public void redo() throws UnexpectedUndoException {
        WorkingContextManager.getInstance(project).clearContext();
      }
    };
    UndoableCommand.execute(project, action, TaskBundle.message("task.clear.context.action.name"), "Context");
  }
}
