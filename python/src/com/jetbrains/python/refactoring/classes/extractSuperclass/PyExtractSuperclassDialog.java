/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.classes.extractSuperclass;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.intellij.refactoring.classMembers.DependencyMemberInfoModel;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.ui.UpDirectedMembersMovingDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public class PyExtractSuperclassDialog  extends UpDirectedMembersMovingDialog {
  private final NamesValidator myNamesValidator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance());
  protected JTextField mySourceClassField;
  protected JLabel mySuperNameLabel;
  protected JTextField myExtractedSuperNameField;
  protected TextFieldWithBrowseButton myTargetDirField;
  protected JLabel myDirLabel;
  private static final String FILE_BROWSER_TITLE = "Extract superclass to file or directory:";

  public PyExtractSuperclassDialog(Project project, PyClass clazz, PyMemberInfoStorage infoStorage) {
    super(project, clazz);
    myMemberInfos = infoStorage.getClassMemberInfos(myClass);

    myExtractedSuperNameField = new JTextField();
    myTargetDirField = new TextFieldWithBrowseButton();
    initSourceClassField();

    setTitle(PyExtractSuperclassHandler.REFACTORING_NAME);

    init();
  }

  protected void initSourceClassField() {
    mySourceClassField = new JTextField();
    mySourceClassField.setEditable(false);
    mySourceClassField.setText(myClass.getName());
  }

  @Override
  protected void doOKAction() {
    final String name = getSuperBaseName();
    if (!myNamesValidator.isIdentifier(name, myClass.getProject())) {
      setErrorText(PyBundle.message("refactoring.extract.super.name.0.must.be.ident", name));
      return;
    }
    boolean found_root = false;
    try {
      String target_dir = FileUtil.toSystemIndependentName(new File(myTargetDirField.getText()).getCanonicalPath());
      for (VirtualFile file : ProjectRootManager.getInstance(myClass.getProject()).getContentRoots()) {
        if (StringUtil.startsWithIgnoreCase(target_dir, file.getPath())) {
          found_root = true;
          break;
        }
      }
    }
    catch (IOException ignore) {
    }
    if (! found_root) {
      setErrorText(PyBundle.message("refactoring.extract.super.target.path.outside.roots"));
      return;
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myExtractedSuperNameField;
  }

  protected JPanel createNorthPanel() {
    Box box = createBox();
    box.add(Box.createVerticalStrut(10));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  protected Box createBox() {
    Box box = Box.createVerticalBox();

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(new JLabel(RefactoringBundle.message("extract.superclass.from")), BorderLayout.NORTH);
    _panel.add(mySourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    mySuperNameLabel = new JLabel();
    mySuperNameLabel.setText(RefactoringBundle.message("superclass.name"));

    _panel = new JPanel(new BorderLayout());
    _panel.add(mySuperNameLabel, BorderLayout.NORTH);
    _panel.add(myExtractedSuperNameField, BorderLayout.CENTER);
    box.add(_panel);
    box.add(Box.createVerticalStrut(5));

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    final VirtualFile root = getRoot();
    assert root != null;

    final Project project = myClass.getProject();
    descriptor.setRoots(ProjectRootManager.getInstance(project).getContentRoots());
    descriptor.setIsTreeRootVisible(true);
    myTargetDirField.setText(FileUtil.toSystemDependentName(root.getPath()));
    myTargetDirField.addBrowseFolderListener(FILE_BROWSER_TITLE,
                                       null, project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    _panel = new JPanel(new BorderLayout());
    myDirLabel = new JLabel();
    myDirLabel.setText(FILE_BROWSER_TITLE);

    _panel.add(myDirLabel, BorderLayout.NORTH);
    _panel.add(myTargetDirField, BorderLayout.CENTER);
    box.add(_panel);
    return box;
  }

  @Nullable
  protected VirtualFile getRoot() {
    return myClass.getContainingFile().getVirtualFile();
  }

  @Override
  protected String getMembersBorderTitle() {
    return RefactoringBundle.message("members.to.form.superclass");
  }

  @Override
  protected String getHelpId() {
    return "python.reference.extractSuperclass";
  }

  @Override
  protected DependencyMemberInfoModel<PyElement, PyMemberInfo> createMemberInfoModel() {
    return new MyMemberInfoModel(myClass);
  }

  @Override
  public boolean checkConflicts() {
    final Collection<PyMemberInfo> infos = getSelectedMemberInfos();
    if (!checkWritable(myClass, infos)) return false;
    if (infos.size() == 0) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(myClass.getProject(), RefactoringBundle.message("no.members.selected"));
      conflictsDialog.show();
      return conflictsDialog.isOK();
    }
    return true;
  }

  public String getSuperBaseName() {
    return myExtractedSuperNameField.getText();
  }

  public String getTargetFile() {
    return myTargetDirField.getText();
  }

  private static class MyMemberInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo> {
    public MyMemberInfoModel(PyClass clazz) {
      super(clazz, null, false);
    }

    public boolean isAbstractEnabled(PyMemberInfo member) {
      return false;
    }

    public int checkForProblems(@NotNull PyMemberInfo member) {
      return member.isChecked() ? OK : super.checkForProblems(member);
    }

    @Override
    protected int doCheck(@NotNull PyMemberInfo memberInfo, int problem) {
      return problem;
    }
  }
}
