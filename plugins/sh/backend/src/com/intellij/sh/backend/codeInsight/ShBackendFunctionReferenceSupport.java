package com.intellij.sh.backend.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.sh.codeInsight.ShFunctionReferenceSupport;
import org.jetbrains.annotations.NotNull;

public class ShBackendFunctionReferenceSupport implements ShFunctionReferenceSupport {
  @Override
  public PsiReference resolveReference(@NotNull PsiElement element) {
    return new ShFunctionReference(element);
  }
}