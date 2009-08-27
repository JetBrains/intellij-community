/*
 * User: anna
 * Date: 25-Aug-2008
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;

public class ElementToArrayConversionRule extends TypeConversionRule{
  public TypeConversionDescriptor findConversion(final PsiType from, final PsiType to, final PsiMember member, final PsiElement context,
                                                 final TypeMigrationLabeler labeler) {
    if (to instanceof PsiArrayType && TypeConversionUtil.isAssignable(((PsiArrayType)to).getComponentType(), from)) {
      return new TypeConversionDescriptor("$qualifier$", "new " + from.getCanonicalText() + "[]{$qualifier$}", (PsiExpression)context);
    }
    return null;
  }
}