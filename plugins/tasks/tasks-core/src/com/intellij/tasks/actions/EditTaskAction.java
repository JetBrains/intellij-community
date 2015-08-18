/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.LocalTaskImpl;

/**
 * @author Dmitry Avdeev
 */
public class EditTaskAction extends BaseTaskAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = getEventProject(e);
    if (project != null) {
      LocalTaskImpl task = (LocalTaskImpl)TaskManager.getManager(project).getActiveTask();
      new EditTaskDialog(project, task).show();
    }
  }

  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    Project project = getEventProject(event);
    if (project != null && presentation.isEnabled()) {
      presentation.setText("Edit '" + TaskManager.getManager(project).getActiveTask().getPresentableName() + "'");
    }
  }
}
