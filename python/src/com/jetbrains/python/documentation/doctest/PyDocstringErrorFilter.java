package com.jetbrains.python.documentation.doctest;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 *
 * Do not highlight syntax errors in doctests
 */
public class PyDocstringErrorFilter extends HighlightErrorFilter {

  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    final PsiFile file = element.getContainingFile();
    return !(file instanceof PyDocstringFile);
  }
}
