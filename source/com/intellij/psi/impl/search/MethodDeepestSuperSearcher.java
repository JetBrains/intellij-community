/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class MethodDeepestSuperSearcher implements QueryExecutor<PsiMethod, PsiMethod> {

  public boolean execute(final PsiMethod method, final Processor<PsiMethod> consumer) {
    final HierarchicalMethodSignature hierarchical = method.getHierarchicalMethodSignature();
    final Set<PsiMethod> methods = new LinkedHashSet<PsiMethod>();
    findDeepestSuperOrSelfSignature(hierarchical, methods);
    for (final PsiMethod psiMethod : methods) {
      if (psiMethod != method && !consumer.process(psiMethod)) {
        return false;
      }
    }
    return true;
  }

  private static void findDeepestSuperOrSelfSignature(HierarchicalMethodSignature signature, final Set<PsiMethod> set) {
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();

    if (supers.isEmpty()) {
      set.add(signature.getMethod());
      return;
    }

    for (HierarchicalMethodSignature superSignature : supers) {
      if (!superSignature.getMethod().hasModifierProperty(PsiModifier.STATIC)) {
        findDeepestSuperOrSelfSignature(superSignature, set);
      }
    }
  }

}
