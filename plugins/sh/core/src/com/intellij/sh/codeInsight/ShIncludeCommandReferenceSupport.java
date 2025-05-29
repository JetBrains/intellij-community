package com.intellij.sh.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

public interface ShIncludeCommandReferenceSupport {
  PsiReference resolveReference(@NotNull PsiElement element);
}