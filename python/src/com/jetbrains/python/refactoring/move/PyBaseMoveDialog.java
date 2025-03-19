// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.move;

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
    var descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle(PyBundle.message("refactoring.move.choose.destination.file.title"))
      .withRoots(ProjectRootManager.getInstance(project).getContentRoots())
      .withTreeRootVisible(true);
    myBrowseFieldWithButton.addBrowseFolderListener(project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
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

  @Override
  protected abstract @Nullable String getDimensionServiceKey();

  @Override
  protected @Nullable JComponent createCenterPanel() {
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

  public @NotNull String getTargetPath() {
    return myBrowseFieldWithButton.getText();
  }
}
