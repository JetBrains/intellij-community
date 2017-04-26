/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.stepic.EduStepikUtils;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyLinkToStepikAction extends StudyActionWithShortcut {
  public static final String SHORTCUT = "ctrl alt pressed HOME";
  public static final String ACTION_ID = "Edu.LinkToStepik";
  private static final String TEXT = "Open this task in Stepik";

  public StudyLinkToStepikAction() {
    super(getTextWithShortcuts(), TEXT, EducationalCoreIcons.Stepik);
  }

  @NotNull
  private static String getTextWithShortcuts() {
    return TEXT + "(" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")";
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    Task currentTask = StudyUtils.getCurrentTask(project);

    String link = EduStepikUtils.getLink(currentTask);
    if (link != null) {
      BrowserUtil.browse(link);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    StudyUtils.updateAction(e);
    Project project = e.getProject();
    if (project == null) {
      presentation.setVisible(false);
      return;
    }
    Task currentTask = StudyUtils.getCurrentTask(project);

    String link = EduStepikUtils.getLink(currentTask);
    boolean visible = link != null;
    presentation.setVisible(visible);
    presentation.setEnabled(visible);
    if (visible) {
      presentation.setText(getTextWithShortcuts() + '\n' + link);
    }
  }

  @NotNull
  @Override
  public String getActionId() {
    return ACTION_ID;
  }

  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT};
  }
}
