/*
 * User: anna
 * Date: 08-Aug-2008
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.Nullable;

public abstract class TypeConversionRule {
  @Nullable
  public abstract TypeConversionDescriptor findConversion(final PsiType from,
                                          final PsiType to,
                                          final PsiMember member,
                                          final PsiExpression context,
                                          final TypeMigrationLabeler labeler);


  @Nullable
  public Pair<PsiType, PsiType> bindTypeParameters(PsiType from, PsiType to, final PsiMethod method, final PsiExpression context,
                                                   final TypeMigrationLabeler labeler) {
    return null;
  }
}