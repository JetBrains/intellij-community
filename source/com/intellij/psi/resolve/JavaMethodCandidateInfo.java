/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;

/**
 * @author peter
 */
public class JavaMethodCandidateInfo {
  private final PsiMethod myMethod;
  private final PsiSubstitutor mySubstitutor;

  public JavaMethodCandidateInfo(PsiMethod method, PsiSubstitutor substitutor) {
    myMethod = method;
    mySubstitutor = substitutor;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }
}
