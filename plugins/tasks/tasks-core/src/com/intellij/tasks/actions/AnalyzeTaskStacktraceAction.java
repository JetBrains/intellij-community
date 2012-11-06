/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.Comment;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.unscramble.AnalyzeStacktraceUtil;

/**
 * @author Dmitry Avdeev
 */
public class AnalyzeTaskStacktraceAction extends BaseTaskAction {

  public void actionPerformed(AnActionEvent e) {
    final LocalTask activeTask = getActiveTask(e);
    final Project project = getProject(e);
    assert activeTask != null;
    assert project != null;
    analyzeStacktrace(activeTask, project);
  }

  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    if (event.getPresentation().isEnabled()) {
      Task activeTask = getActiveTask(event);
      event.getPresentation().setEnabled(activeTask != null && hasTexts(activeTask));
    }
  }

  public static boolean hasTexts(Task activeTask) {
    return (activeTask.getDescription() != null || activeTask.getComments().length > 0);
  }

  public static void analyzeStacktrace(Task task, Project project) {

    ChooseStacktraceDialog stacktraceDialog = new ChooseStacktraceDialog(project, task);
    stacktraceDialog.show();
    if (stacktraceDialog.isOK() && stacktraceDialog.getTraces().length > 0) {
      Comment[] comments = stacktraceDialog.getTraces();
      for (Comment comment : comments) {
        AnalyzeStacktraceUtil.addConsole(project, null, task.getId(), comment.getText());
      }
    }
  }
}
