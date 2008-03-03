/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class SamePackageWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    Module elementModule = ModuleUtil.findModuleForPsiElement(element);
    if (location.getPositionModule() == elementModule) {
      final PsiPackage psiPackage = PsiTreeUtil.getContextOfType(location.getPosition(), PsiPackage.class, false);
      if (psiPackage != null && psiPackage.equals(PsiTreeUtil.getContextOfType(element, PsiPackage.class, false))) return true;
    }
    return false;
  }
}
