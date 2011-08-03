/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.doc.TaskPsiElement;

/**
 * @author Dennis.Ushakov
 */
public class ShowTaskDescription extends AnAction {
  @Override
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final LocalTask task = project != null ? TaskManager.getManager(project).getActiveTask() : null;
    presentation.setEnabled(task != null && task.getIssueUrl() != null);
    if (project == null || !TaskManager.getManager(project).getActiveTask().isIssue()) {
      presentation.setText(getTemplatePresentation().getText());
    } else {
      presentation.setText("Show '" + TaskManager.getManager(project).getActiveTask().getPresentableName() + "' _Description");
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final LocalTask task = TaskManager.getManager(project).getActiveTask();

    try {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.quickjavadoc.ctrln");
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          DocumentationManager.getInstance(project).showJavaDocInfo(new TaskPsiElement(PsiManager.getInstance(project), task), null);
        }
      }, getCommandName(), null);
    } catch (IndexNotReadyException e1) {
      DumbService.getInstance(project).showDumbModeNotification("Documentation is not available until indices are built");
    }
  }

  protected String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text != null ? text : "";
  }
}
