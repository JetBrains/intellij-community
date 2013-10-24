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
package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionDialog extends RefactoringDialog {
  private PyMoveClassOrFunctionPanel myPanel;

  public PyMoveClassOrFunctionDialog(@NotNull Project project, @NotNull PsiNamedElement[] elements, @Nullable String destination) {
    super(project, true);
    assert elements.length > 0;
    final String moveText;

    if (elements.length == 1) {
      PsiNamedElement e = elements[0];
      if (e instanceof PyClass) {
        moveText = PyBundle.message("refactoring.move.class.$0", ((PyClass)e).getQualifiedName());
      }
      else {
        moveText = PyBundle.message("refactoring.move.function.$0", e.getName());
      }
    }
    else {
      moveText = PyBundle.message("refactoring.move.selected.elements");
    }

    if (destination == null) {
      destination = getContainingFileName(elements[0]);
    }

    myPanel = new PyMoveClassOrFunctionPanel(moveText, destination);
    setTitle(PyBundle.message("refactoring.move.class.or.function.dialog.title"));

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    descriptor.setRoots(ProjectRootManager.getInstance(project).getContentRoots());
    descriptor.setIsTreeRootVisible(true);

    myPanel.getBrowseTargetFileButton().addBrowseFolderListener(PyBundle.message("refactoring.move.class.or.function.choose.destination.file.title"),
                                                                null, project, descriptor,
                                                                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doAction() {
    close(OK_EXIT_CODE);
  }

  @Override
  protected String getHelpId() {
    return "python.reference.moveClass";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getBrowseTargetFileButton().getTextField();
  }

  public String getTargetPath() {
    return myPanel.getBrowseTargetFileButton().getText();
  }

  private static String getContainingFileName(PsiElement element) {
    VirtualFile file = element.getContainingFile().getVirtualFile();
    if (file != null) {
      return FileUtil.toSystemDependentName(file.getPath());
    }
    else {
      return "";
    }
  }
}
