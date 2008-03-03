/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class SamePsiMemberWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    final PsiElement commonContext = PsiTreeUtil.findCommonContext(location.getPosition(), element);
    if (PsiTreeUtil.getContextOfType(commonContext, PsiMethod.class, false) != null) return 2;
    if (PsiTreeUtil.getContextOfType(commonContext, PsiClass.class, false) != null) return 1;
    return 0;
  }
}
