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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.jetbrains.edu.learning.StudyPluginConfigurator;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyJavaFxToolWindow extends StudyToolWindow {
  private StudyBrowserWindow myBrowserWindow;

  public StudyJavaFxToolWindow() {
    super();
  }

  @Override
  public JComponent createTaskInfoPanel(Project project) {
    myBrowserWindow = new StudyBrowserWindow(project, true, false);
    myBrowserWindow.addBackAndOpenButtons();
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
    panel.add(myBrowserWindow.getPanel());
    return panel;
  }

  @Override
  public void setText(@NotNull String text) {
    StudyPluginConfigurator configurator = StudyUtils.getConfigurator(ProjectUtil.guessCurrentProject(this));
    myBrowserWindow.loadContent(text, configurator);
  }
}
