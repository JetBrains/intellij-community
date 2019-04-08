package com.intellij.bash;

import com.intellij.bash.psi.BashFile;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BashErrorFilter extends HighlightErrorFilter implements HighlightInfoFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    if (ApplicationManager.getApplication().isInternal()) return true;
    return !(element.getContainingFile() instanceof BashFile);
  }

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (ApplicationManager.getApplication().isInternal()) return true;
    if (UpdateHighlightersUtil.isFileLevelOrGutterAnnotation(highlightInfo)) return true;
    if (!(file instanceof BashFile)) return true;
    return highlightInfo.getSeverity().compareTo(HighlightSeverity.WARNING) < 0;
  }
}