// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ShErrorFilter extends HighlightErrorFilter implements HighlightInfoFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    if (ApplicationManager.getApplication().isInternal()) return true;
    return !(element.getContainingFile() instanceof ShFile);
  }

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile psiFile) {
    if (ApplicationManager.getApplication().isInternal()) return true;
    if (UpdateHighlightersUtil.isFileLevelOrGutterAnnotation(highlightInfo)) return true;
    if (!(psiFile instanceof ShFile)) return true;
    return highlightInfo.getSeverity().compareTo(HighlightSeverity.WARNING) < 0;
  }
}