/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.TaskManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class CloseTaskAction extends BaseTaskAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(project);
    LocalTask task = taskManager.getActiveTask();
    CloseTaskDialog dialog = new CloseTaskDialog(project, task);
    if (dialog.showAndGet()) {
      final CustomTaskState taskState = dialog.getCloseIssueState();
      if (taskState != null) {
        try {
          TaskRepository repository = task.getRepository();
          assert repository != null;
          repository.setTaskState(task, taskState);
          repository.setPreferredCloseTaskState(taskState);
        }
        catch (Exception e1) {
          Messages.showErrorDialog(project, e1.getMessage(), TaskBundle.message("dialog.title.cannot.set.state.for.issue"));
        }
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = getProject(event);
    boolean enabled = project != null && !TaskManager.getManager(project).getActiveTask().isDefault();
    presentation.setEnabled(enabled);
  }
}
