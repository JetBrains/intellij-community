/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.occurences;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
public abstract class NotInSuperOrThisCallFilterBase implements OccurenceFilter {
  public boolean isOK(PsiExpression occurence) {
    PsiElement parent = occurence.getParent();
    while(parent instanceof PsiExpression) {
      parent = parent.getParent();
    }
    if(!(parent instanceof PsiExpressionList)) return true;
    parent = parent.getParent();
    if(!(parent instanceof PsiMethodCallExpression)) return true;
    final String text = ((PsiMethodCallExpression) parent).getMethodExpression().getText();
    return !getKeywordText().equals(text);
  }

  protected abstract @NonNls String getKeywordText();
}
