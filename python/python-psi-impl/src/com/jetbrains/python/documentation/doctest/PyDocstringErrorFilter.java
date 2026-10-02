// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 *
 * Do not highlight syntax errors in doctests
 */
@ApiStatus.Internal
public final class PyDocstringErrorFilter extends HighlightErrorFilter {

  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyDoctestFile) return false;

    return DocStringUtil.getDocstringInjectionHost(file) == null;
  }
}
