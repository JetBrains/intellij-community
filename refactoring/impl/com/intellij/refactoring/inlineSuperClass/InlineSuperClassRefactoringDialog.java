/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InlineSuperClassRefactoringDialog extends RefactoringDialog{
  private final PsiClass mySuperClass;
  private final PsiClass[] myTargetClasses;

  protected InlineSuperClassRefactoringDialog(@NotNull Project project, PsiClass superClass, final PsiClass... targetClasses) {
    super(project, false);
    mySuperClass = superClass;
    myTargetClasses = targetClasses;
    init();
    setTitle(InlineSuperClassRefactoringHandler.REFACTORING_NAME);
  }

  protected void doAction() {
    invokeRefactoring(new InlineSuperClassRefactoringProcessor(getProject(), mySuperClass, myTargetClasses));
  }

  protected JComponent createCenterPanel() {
    return new JLabel("Inline \'" + mySuperClass.getQualifiedName() + "\' to \'" + StringUtil.join(myTargetClasses, new Function<PsiClass, String>() {
      public String fun(final PsiClass psiClass) {
        return psiClass.getQualifiedName();
      }
    }, ", ") + "\'");
  }
}