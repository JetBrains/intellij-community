package com.jetbrains.python.psi;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

public interface PsiReferenceEx extends PsiReference {
  @Nullable
  HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context);
  @Nullable String getUnresolvedDescription();
}
