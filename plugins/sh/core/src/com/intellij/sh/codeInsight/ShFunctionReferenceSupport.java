package com.intellij.sh.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ShFunctionReferenceSupport {
  PsiReference resolveReference(@NotNull PsiElement element);
}