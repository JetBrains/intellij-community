package com.intellij.refactoring.util.occurences;

import com.intellij.psi.*;

public interface OccurenceFilter {
  boolean isOK(PsiExpression occurence);
}
