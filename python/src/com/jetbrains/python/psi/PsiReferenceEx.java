package com.jetbrains.python.psi;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

public interface PsiReferenceEx extends PsiReference {
  @Nullable
  HighlightSeverity getUnresolvedHighlightSeverity();
  @Nullable String getUnresolvedDescription();
}
