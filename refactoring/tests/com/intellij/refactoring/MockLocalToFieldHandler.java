package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.introduceField.LocalToFieldHandler;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiModifier;

/**
 * @author ven
 */
public class MockLocalToFieldHandler extends LocalToFieldHandler {
  public MockLocalToFieldHandler(Project project, boolean isConstant) {
    super(project, isConstant);
  }

  protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(PsiClass aClass, PsiLocalVariable local, PsiExpression[] occurences,
                                                                        boolean isStatic) {
    return new BaseExpressionToFieldHandler.Settings("xxx", true, isStatic, true, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
                                                     PsiModifier.PRIVATE, local, null, false, aClass, true);
  }
}