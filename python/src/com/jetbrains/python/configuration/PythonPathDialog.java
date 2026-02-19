// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.ui.IdeaDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.Dimension;

public class PythonPathDialog extends IdeaDialog {
  private final PythonPathEditor myEditor;

  public PythonPathDialog(final @NotNull Project project, final @NotNull PythonPathEditor editor) {
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
