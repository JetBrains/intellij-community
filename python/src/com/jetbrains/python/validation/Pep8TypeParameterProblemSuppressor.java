// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyTypeParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Pep8TypeParameterProblemSuppressor implements Pep8ProblemSuppressor {

  public static final String UNEXPECTED_SPACES_AROUND_KEYWORD = "E251";
  public static final String MISSING_WHITESPACE_AROUND_OPERATOR = "E225";

  @Override
  public boolean isProblemSuppressed(Pep8ExternalAnnotator.@NotNull Problem problem,
                                     @NotNull PsiFile file,
                                     @Nullable PsiElement targetElement) {
    if (targetElement != null) {
      return ((problem.getCode().equals(UNEXPECTED_SPACES_AROUND_KEYWORD)
               || problem.getCode().equals(MISSING_WHITESPACE_AROUND_OPERATOR))
              && targetElement.getParent() instanceof PyTypeParameter);
    }
    return false;
  }
}
