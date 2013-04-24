package com.jetbrains.python.refactoring.invertBoolean;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageViewUtil;

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

  public PyInvertBooleanDialog(final PsiElement element) {
    super(element.getProject(), false);
    myElement = element;
    final String name = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : element.getText();
    myNameField.setText(name);
    myLabel.setLabelFor(myNameField);
    final String typeString = UsageViewUtil.getType(myElement);
    myLabel.setText(RefactoringBundle.message("invert.boolean.name.of.inverted.element", typeString));
    myCaptionLabel.setText(RefactoringBundle.message("invert.0.1",
                                                     typeString,
                                                     UsageViewUtil.getDescriptiveName(myElement)));

    setTitle(PyInvertBooleanHandler.REFACTORING_NAME);
    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected void doAction() {
    Project project = myElement.getProject();
    final String name = myNameField.getText().trim();
    if (name.length() == 0 || !RenameUtil.isValidName(myProject, myElement, name)) {
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
}
