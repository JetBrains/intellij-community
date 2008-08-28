/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.ui.RefactoringDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InlineSuperClassRefactoringDialog extends RefactoringDialog{
  private final PsiClass mySuperClass;
  private final PsiClass myTargetClass;

  protected InlineSuperClassRefactoringDialog(@NotNull Project project, PsiClass superClass, final PsiClass psiClass) {
    super(project, false);
    mySuperClass = superClass;
    myTargetClass = psiClass;
    init();
    setTitle(InlineSuperClassRefactoringHandler.REFACTORING_NAME);
  }

  protected void doAction() {
    invokeRefactoring(new InlineSuperClassRefactoringProcessor(getProject(), mySuperClass, myTargetClass));
  }

  protected JComponent createCenterPanel() {
    return null;
  }
}