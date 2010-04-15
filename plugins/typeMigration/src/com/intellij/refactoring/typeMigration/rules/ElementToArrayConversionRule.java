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
  public TypeConversionDescriptor findConversion(final PsiType from, final PsiType to, final PsiMember member, final PsiExpression context,
                                                 final TypeMigrationLabeler labeler) {
    if (member == null && to instanceof PsiArrayType && TypeConversionUtil.isAssignable(((PsiArrayType)to).getComponentType(), from)) {
      final PsiElement parent = context.getParent();
      if ((context instanceof PsiLiteralExpression || context instanceof PsiReferenceExpression) && parent instanceof PsiStatement) {
        return new TypeConversionDescriptor("$qualifier$", "new " + from.getCanonicalText() + "[]{$qualifier$}", context);
      }
    }
    return null;
  }
}