// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PythonVisitorFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PyMultiplePsiFilesVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(@NotNull Class visitorClass, @NotNull PsiFile file) {
    if (visitorClass == PyStringLiteralQuotesAnnotatorVisitor.class &&
        file.getViewProvider() instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
      return false;
    }
    return true;
  }
}
