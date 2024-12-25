// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class InjectedHtmlErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(final @NotNull PsiErrorElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile.getLanguage() == HTMLLanguage.INSTANCE && containingFile.getContext() != null) {
      return false;
    }

    return true;
  }

}
