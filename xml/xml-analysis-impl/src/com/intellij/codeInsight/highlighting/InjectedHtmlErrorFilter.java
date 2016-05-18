package com.intellij.codeInsight.highlighting;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class InjectedHtmlErrorFilter extends HighlightErrorFilter {
  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile.getLanguage() == HTMLLanguage.INSTANCE && containingFile.getContext() != null) {
      if (isErrorToBeFiltered(element.getErrorDescription())) {
        return false;
      }
    }

    return true;
  }

  private static boolean isErrorToBeFiltered(@NotNull final String errorDescription) {
    return errorDescription.contains("is not closed") ||
           errorDescription.contains("is not completed") ||
           errorDescription.contains("expected") ||
           errorDescription.contains("not terminated") ||
           errorDescription.contains("Unclosed string") ||
           errorDescription.contains("Unexpected tokens");
  }
}
