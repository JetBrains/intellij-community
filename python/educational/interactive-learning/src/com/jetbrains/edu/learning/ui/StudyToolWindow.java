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
package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.ui.JBUI;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.editor.StudyEditor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class StudyToolWindow extends SimpleToolWindowPanel implements DataProvider, Disposable {

  private final JPanel myToolbarPanel;

  public StudyToolWindow(final Project project) {
    super(true, true);
    myToolbarPanel = createToolbarPanel();
    setToolbar(myToolbarPanel);

    final StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    if (studyEditor == null) return;
    Task task = studyEditor.getTaskFile().getTask();

    if (task != null) {
      final String taskText = task.getText();

      final JTextPane taskTextPane = new JTextPane();
      taskTextPane.setContentType("text/html");
      taskTextPane.setEditable(false);
      taskTextPane.setText(taskText);
      taskTextPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
      taskTextPane.setBackground(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
      taskTextPane.setBorder(new EmptyBorder(15, 20, 0, 100));
      setContent(taskTextPane);
    }
  }

  public void dispose() {
  }

  private static JPanel createToolbarPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new StudyCheckAction());
    group.add(new StudyPreviousStudyTaskAction());
    group.add(new StudyNextStudyTaskAction());
    group.add(new StudyRefreshTaskFileAction());
    group.add(new StudyShowHintAction());

    group.add(new StudyRunAction());
    group.add(new StudyEditInputAction());

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Study", group, true);
    return JBUI.Panels.simplePanel(actionToolBar.getComponent());
  }

}
