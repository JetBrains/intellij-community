/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.scopeView.nodes;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class MethodNode extends BasePsiNode<PsiMethod> {

  public MethodNode(final PsiMethod element) {
    super(element);
  }

  public String toString() {
    final PsiMethod method = (PsiMethod)getPsiElement();
    if (method == null || !method.isValid()) return "";
    String name = PsiFormatUtil.formatMethod(
      method,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    return name;
  }

  public int getWeight() {
    return 5;
  }

  @Override
  public boolean isDeprecated() {
    final PsiMethod psiMethod = (PsiMethod)getPsiElement();
    return psiMethod != null && psiMethod.isDeprecated();
  }
}
