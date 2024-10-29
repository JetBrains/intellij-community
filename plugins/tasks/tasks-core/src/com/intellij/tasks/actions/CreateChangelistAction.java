// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.tasks.impl.TaskUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class CreateChangelistAction extends BaseTaskAction {

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    if (event.getPresentation().isEnabled()) {
      Project project = getProject(event);
      TaskManager manager = getTaskManager(event);
      Presentation presentation = event.getPresentation();

      if (project == null ||
          manager == null ||
          !manager.isVcsEnabled() ||
          !ChangeListManager.getInstance(project).areChangeListsEnabled()) {
        presentation.setTextWithMnemonic(getTemplatePresentation().getTextWithPossibleMnemonic());
        presentation.setEnabled(false);
      }
      else {
        presentation.setEnabled(true);
        if (manager.getActiveTask().getChangeLists().size() == 0) {
          presentation.setText(TaskBundle.message("action.create.changelist.for.text", TaskUtil.getTrimmedSummary(manager.getActiveTask())));
        }
        else {
          presentation.setText(TaskBundle.message("action.add.changelist.for.text", TaskUtil.getTrimmedSummary(manager.getActiveTask())));
        }
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    TaskManagerImpl manager = (TaskManagerImpl)getTaskManager(e);
    assert manager != null;
    LocalTask activeTask = manager.getActiveTask();
    String name =
      Messages.showInputDialog(getProject(e), TaskBundle.message("dialog.message.changelist.name"),
                               TaskBundle.message("dialog.title.create.changelist"), null, manager.getChangelistName(activeTask), null);
    if (name != null) {
      manager.createChangeList(activeTask, name);
    }
  }
}
