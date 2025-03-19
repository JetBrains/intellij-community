// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.components.JBBox;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedViewSwingImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Ilya.Kazakevich
 */
class PyExtractSuperclassViewSwingImpl
  extends MembersBasedViewSwingImpl<PyExtractSuperclassPresenter, PyExtractSuperclassInitializationInfo>
  implements PyExtractSuperclassView {

  private final @NotNull JTextArea myExtractedSuperNameField = new JTextArea();
  private final @NotNull FileChooserDescriptor myFileChooserDescriptor;
  private final @NotNull TextFieldWithBrowseButton myTargetDirField;

  PyExtractSuperclassViewSwingImpl(final @NotNull PyClass classUnderRefactoring,
                                   final @NotNull Project project,
                                   final @NotNull PyExtractSuperclassPresenter presenter) {
    super(project, presenter, RefactoringBundle.message("extract.superclass.from"), true);
    setTitle(PyExtractSuperclassHandler.getRefactoringName());


    final JBBox box = JBBox.createVerticalBox();

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(RefactoringBundle.message("extract.superclass.from")), BorderLayout.NORTH);
    final JTextField sourceClassField = new JTextField();
    sourceClassField.setEditable(false);
    sourceClassField.setText(classUnderRefactoring.getName());
    panel.add(sourceClassField, BorderLayout.CENTER);
    box.add(panel);

    box.add(Box.createVerticalStrut(10));

    final JLabel superNameLabel = new JLabel();
    superNameLabel.setText(RefactoringBundle.message("superclass.name"));

    panel = new JPanel(new BorderLayout());
    panel.add(superNameLabel, BorderLayout.NORTH);
    panel.add(myExtractedSuperNameField, BorderLayout.CENTER);
    box.add(panel);
    box.add(Box.createVerticalStrut(5));

    myFileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
      .withTitle(getFileOrDirectory())
      .withRoots(ProjectRootManager.getInstance(project).getContentRoots())
      .withTreeRootVisible(true);
    myTargetDirField = new TextFieldWithBrowseButton();
    myTargetDirField.addBrowseFolderListener(project, myFileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    panel = new JPanel(new BorderLayout());
    final JLabel dirLabel = new JLabel();
    dirLabel.setText(getFileOrDirectory()); //u18n

    panel.add(dirLabel, BorderLayout.NORTH);
    panel.add(myTargetDirField, BorderLayout.CENTER);
    box.add(panel);

    box.add(Box.createVerticalStrut(10));


    myTopPanel.add(box, BorderLayout.CENTER);
    myCenterPanel.add(myPyMemberSelectionPanel, BorderLayout.CENTER);
    setPreviewResults(false);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myExtractedSuperNameField;
  }

  @Override
  public void configure(final @NotNull PyExtractSuperclassInitializationInfo configInfo) {
    super.configure(configInfo);
    myFileChooserDescriptor.setRoots(configInfo.getRoots());
    myTargetDirField.setText(configInfo.getDefaultFilePath());
  }

  @Override
  public @NotNull String getModuleFile() {
    return myTargetDirField.getText();
  }

  @Override
  public @NotNull String getSuperClassName() {
    return myExtractedSuperNameField.getText();
  }

  @Override
  protected @Nullable String getHelpId() {
    return "refactoring.extract.superclass.dialog";
  }

  private static @Nls String getFileOrDirectory() {
    return RefactoringBundle.message("extract.superclass.elements.header");
  }
}
