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
package com.jetbrains.python.refactoring.invertBoolean;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User : ktisha
 */
public class PyInvertBooleanDialog extends RefactoringDialog {
  private JTextField myNameField;
  private JPanel myPanel;
  private JLabel myLabel;
  private JLabel myCaptionLabel;

  private final PsiElement myElement;
  private final String myName;

  public PyInvertBooleanDialog(final PsiElement element) {
    super(element.getProject(), false);
    myElement = element;
    myName = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : element.getText();
    myNameField.setText(myName);
    myLabel.setLabelFor(myNameField);
    final String typeString = UsageViewUtil.getType(myElement);
    myLabel.setText(RefactoringBundle.message("invert.boolean.name.of.inverted.element", typeString));
    myCaptionLabel.setText(RefactoringBundle.message("invert.0.1",
                                                     typeString,
                                                     DescriptiveNameUtil.getDescriptiveName(myElement)));

    setTitle(PyInvertBooleanHandler.REFACTORING_NAME);
    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected void doAction() {
    Project project = myElement.getProject();
    final String name = myNameField.getText().trim();
    if (name.length() == 0 || (!name.equals(myName) && !RenameUtil.isValidName(myProject, myElement, name))) {
      CommonRefactoringUtil.showErrorMessage(PyInvertBooleanHandler.REFACTORING_NAME,
                                             RefactoringBundle.message("please.enter.a.valid.name.for.inverted.element",
                                                                       UsageViewUtil.getType(myElement)),
                                             "refactoring.invertBoolean", project);
      return;
    }

    invokeRefactoring(new PyInvertBooleanProcessor(myElement, name));
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.invert.boolean";
  }
}
