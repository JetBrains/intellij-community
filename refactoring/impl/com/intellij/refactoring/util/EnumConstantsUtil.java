/*
 * User: anna
 * Date: 03-Sep-2008
 */
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

public class EnumConstantsUtil {
  public static boolean isSuitableForEnumConstant(PsiType constantType, PsiClass enumClass) {
    if (enumClass.isEnum()) {
      for (PsiMethod constructor : enumClass.getConstructors()) {
        final PsiParameter[] parameters = constructor.getParameterList().getParameters();
        if (parameters.length == 1 && TypeConversionUtil.isAssignable(parameters[0].getType(), constantType)) return true;
      }
    }
    return false;
  }

  public static PsiEnumConstant createEnumConstant(PsiClass enumClass, String constantName, PsiExpression initializerExpr) throws
                                                                                                                              IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(enumClass.getProject()).getElementFactory();
    final String enumConstantText = initializerExpr != null ? constantName + "(" + initializerExpr.getText() + ")" : constantName;
    return elementFactory.createEnumConstantFromText(enumConstantText, enumClass);
  }

  public static PsiEnumConstant createEnumConstant(PsiClass enumClass, PsiLocalVariable local, final String fieldName) throws IncorrectOperationException {
    return createEnumConstant(enumClass, fieldName, local.getInitializer());
  }
}