/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.filters.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeCodeFragment;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;

/**
 * @author dsl
 */
public class TypeCodeFragmentIsVoidEnabledFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    return context instanceof PsiTypeCodeFragment &&
            ((PsiTypeCodeFragment)context).isVoidValid();
  }


  public boolean isClassAcceptable(Class hintClass) {
    return ReflectionCache.isAssignable(hintClass, PsiTypeCodeFragment.class);
  }
}
