/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.util.QueryExecutor;
import com.intellij.util.Processor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;

/**
 * @author peter
 */
public class MethodDeepestSuperSearcher implements QueryExecutor<PsiMethod, PsiMethod> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.MethodDeepestSuperSearcher");

  public boolean execute(final PsiMethod method, final Processor<PsiMethod> consumer) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return true;

    HierarchicalMethodSignatureImpl hierarchical = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(aClass, method);
    LOG.assertTrue(hierarchical != null);
    HierarchicalMethodSignature deepest = findDeepestSuperOrSelfSignature(hierarchical);
    if (deepest == hierarchical) return true;
    if (deepest != null) return consumer.process(method);

    return true;
  }

  private HierarchicalMethodSignature findDeepestSuperOrSelfSignature(HierarchicalMethodSignature signature) {
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();

    if (supers.size() == 1) return findDeepestSuperOrSelfSignature(supers.get(0));

    for (HierarchicalMethodSignature superSignature : supers) {
      PsiMethod superMethod = superSignature.getMethod();
      if (!superMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return findDeepestSuperOrSelfSignature(superSignature);
      }
    }
    return signature;
  }

}
