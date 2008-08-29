/*
 * User: anna
 * Date: 29-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceConstructorUsageInfo extends FixableUsageInfo{
  private final PsiType myNewType;
  private String myConflict;
  private static final String CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND = "Constructor matching super not found";

  public ReplaceConstructorUsageInfo(PsiNewExpression element, final PsiClass targetClass, PsiType newType) {
    super(element);
    myNewType = newType;
    final PsiMethod[] constructors = targetClass.getConstructors();
    final PsiMethod constructor = element.resolveConstructor();
    if (constructor == null) {
      if (constructors.length == 1 && constructors[0].getParameterList().getParametersCount() > 0 || constructors.length > 1) {
        myConflict = CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND;
      }
    } else {
      boolean foundMatchingConstructor = false;
      final PsiParameter[] superParameters = constructor.getParameterList().getParameters();
      constr: for (PsiMethod method : constructors) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (superParameters.length == parameters.length) {
          for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (!TypeConversionUtil.isAssignable(TypeConversionUtil.erasure(parameter.getType()),
                                                 TypeConversionUtil.erasure(superParameters[i].getType()))) {
              continue constr;
            }
          }
          foundMatchingConstructor = true;
        }
      }
      if (!foundMatchingConstructor) {
        myConflict = CONSTRUCTOR_MATCHING_SUPER_NOT_FOUND;
      }

    }
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiNewExpression newExpression = (PsiNewExpression)getElement();
    if (newExpression != null) {
      final PsiExpression expr =
        JavaPsiFacade.getInstance(newExpression.getProject()).getElementFactory().createExpressionFromText("new " + myNewType.getCanonicalText() + newExpression.getArgumentList().getText(), newExpression);
      newExpression.replace(expr);
    }
  }

  public String getConflictMessage() {
    return myConflict;
  }
}