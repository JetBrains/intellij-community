package com.intellij.codeInsight.problems;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

/**
 * @author cdr
 */
public class Problem {
  public VirtualFile virtualFile;
  public HighlightInfo highlightInfo;

  public Problem(final VirtualFile virtualFile, final HighlightInfo highlightInfo) {
    this.virtualFile = virtualFile;
    this.highlightInfo = highlightInfo;
  }
}
