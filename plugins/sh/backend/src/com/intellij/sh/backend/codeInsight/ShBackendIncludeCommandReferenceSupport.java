package com.intellij.sh.backend.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.sh.codeInsight.ShIncludeCommandReferenceSupport;
import org.jetbrains.annotations.NotNull;

public class ShBackendIncludeCommandReferenceSupport implements ShIncludeCommandReferenceSupport {
  @Override
  public PsiReference resolveReference(@NotNull PsiElement element) {
    return new ShIncludeCommandReference(element);
  }
}