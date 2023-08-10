// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.html.HtmlFileImpl;
import org.jetbrains.annotations.NotNull;

public class HtmlNoBracketCompletionEnablerImpl implements HtmlInTextCompletionEnabler {
  @Override
  public boolean isEnabledInFile(final @NotNull PsiFile file) {
    return file instanceof HtmlFileImpl;
  }
}
