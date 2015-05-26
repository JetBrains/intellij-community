/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.ui.IdeaDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class PythonPathDialog extends IdeaDialog {
  private PythonPathEditor myEditor;

  public PythonPathDialog(@NotNull final Project project, @NotNull final PythonPathEditor editor) {
    super(project);
    myEditor = editor;
    init();
    setTitle(PyBundle.message("sdk.paths.dialog.title"));
  }

  @Override
  protected JComponent createCenterPanel() {
    JComponent mainPanel = myEditor.createComponent();
    mainPanel.setPreferredSize(new Dimension(600, 400));
    mainPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.ALL));

    return mainPanel;
  }

}
