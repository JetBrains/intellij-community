/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class JavaInheritanceWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    final PsiElement position = location.getPosition();
    PsiClass placeClass = PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
    if (position.getParent() instanceof PsiReferenceExpression) {
      final PsiExpression qualifierExpression = ((PsiReferenceExpression)position.getParent()).getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiType type = qualifierExpression.getType();
        if (type instanceof PsiClassType) {
          final PsiClass psiClass = ((PsiClassType)type).resolve();
          if (psiClass != null) {
            placeClass = psiClass;
          }
        }
      }
    }

    if (element instanceof PsiClass && isTooGeneral((PsiClass)element)) return false;
    if (element instanceof PsiMethod && isTooGeneral(((PsiMethod)element).getContainingClass())) return false;

    PsiClass contextClass = PsiTreeUtil.getContextOfType(position, PsiClass.class, false);
    while (contextClass != null) {
      PsiClass elementClass = placeClass;
      while (elementClass != null) {
        if (contextClass.isInheritor(elementClass, true)) return true;
        elementClass = elementClass.getContainingClass();
      }
      contextClass = contextClass.getContainingClass();
    }
    return false;
  }

  private static boolean isTooGeneral(@Nullable final PsiClass element) {
    if (element == null) return true;

    @NonNls final String qname = element.getQualifiedName();
    return qname == null || qname.startsWith("java.lang.");
  }
}
