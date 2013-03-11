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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.doc.TaskPsiElement;

/**
 * @author Dennis.Ushakov
 */
public class ShowTaskDescription extends BaseTaskAction {
  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    if (event.getPresentation().isEnabled()) {
      final Presentation presentation = event.getPresentation();
      final LocalTask activeTask = getActiveTask(event);
      presentation.setEnabled(activeTask != null && activeTask.isIssue() && activeTask.getDescription() != null);
      if (activeTask == null || !activeTask.isIssue()) {
        presentation.setText(getTemplatePresentation().getText());
      } else {
        presentation.setText("Show '" + activeTask.getPresentableName() + "' _Description");
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getProject(e);
    assert project != null;
    final LocalTask task = getActiveTask(e);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.quickjavadoc.ctrln");
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        DocumentationManager.getInstance(project).showJavaDocInfo(new TaskPsiElement(PsiManager.getInstance(project), task), null);
      }
    }, getCommandName(), null);
  }

  protected String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text != null ? text : "";
  }
}
