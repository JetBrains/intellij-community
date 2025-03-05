// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

  private static @Nullable String getIssueUrl(AnActionEvent event) {
    Project project = event.getProject();
    return project == null ? null : TaskManager.getManager(project).getActiveTask().getIssueUrl();
  }
}
