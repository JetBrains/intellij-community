package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiFile;

public interface HighlightInfoFilter {
  /**
   * @param file - might (and will be) null. Return true in this case if you'd like to switch this kind of highlighting in ANY file
   */
  boolean accept(HighlightInfoType highlightType, PsiFile file);
}

