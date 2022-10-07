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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class OpenTaskInBrowserAction extends BaseTaskAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String url = getIssueUrl(e);
    if (url != null) {
      BrowserUtil.browse(url);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    if (event.getPresentation().isEnabled()) {
      Presentation presentation = event.getPresentation();
      String url = getIssueUrl(event);
      presentation.setEnabled(url != null);
      Project project = getProject(event);
      if (project == null || !TaskManager.getManager(project).getActiveTask().isIssue()) {
        presentation.setTextWithMnemonic(getTemplatePresentation().getTextWithPossibleMnemonic());
      } else {
        presentation.setText(TaskBundle.message("action.open.in.browser.text",
                                                StringUtil.escapeMnemonics(TaskManager.getManager(project).getActiveTask().getPresentableName())));
      }
    }
  }

  @Nullable
  private static String getIssueUrl(AnActionEvent event) {
    Project project = event.getProject();
    return project == null ? null : TaskManager.getManager(project).getActiveTask().getIssueUrl();
  }
}
