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
package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseMoveDialog extends RefactoringDialog {
  protected JPanel myCenterPanel;
  protected JPanel myExtraPanel;
  protected TextFieldWithBrowseButton myBrowseFieldWithButton;
  protected JBLabel myDescription;
  protected JTextField mySourcePathField;

  public PyBaseMoveDialog(Project project, @NotNull String sourcePath, @NotNull String destinationPath) {
    super(project, true);
    mySourcePathField.setText(sourcePath);
    myBrowseFieldWithButton.setText(destinationPath);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    descriptor.setRoots(ProjectRootManager.getInstance(project).getContentRoots());
    descriptor.withTreeRootVisible(true);
    myBrowseFieldWithButton.addBrowseFolderListener(PyBundle.message("refactoring.move.choose.destination.file.title"),
                                                    null,
                                                    project,
                                                    descriptor,
                                                    TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
  }

  @Override
  protected void init() {
    super.init();
    setUpDialog();
  }

  protected void setUpDialog() {
    preselectLastPathComponent(myBrowseFieldWithButton.getTextField());
  }

  protected static void preselectLastPathComponent(@NotNull JTextField field) {
    final String text = field.getText();
    final int start = text.lastIndexOf(File.separatorChar);
    final int lastDotIndex = text.lastIndexOf('.');
    final int end = lastDotIndex < 0 ? text.length() : lastDotIndex;
    if (start + 1 < end) {
      field.select(start + 1, end);
    }
    field.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true);
  }

  @Override
  protected abstract String getHelpId();

  @Nullable
  @Override
  protected abstract String getDimensionServiceKey();

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  @Override
  protected void doAction() {
    close(OK_EXIT_CODE);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBrowseFieldWithButton.getTextField();
  }

  @NotNull
  public String getTargetPath() {
    return myBrowseFieldWithButton.getText();
  }
}
