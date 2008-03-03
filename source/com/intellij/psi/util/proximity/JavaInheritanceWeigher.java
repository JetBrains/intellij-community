/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

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
}
