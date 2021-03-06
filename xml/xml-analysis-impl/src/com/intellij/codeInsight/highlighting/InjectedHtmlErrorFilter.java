package com.intellij.codeInsight.highlighting;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class InjectedHtmlErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile.getLanguage() == HTMLLanguage.INSTANCE && containingFile.getContext() != null) {
      return false;
    }

    return true;
  }

}
