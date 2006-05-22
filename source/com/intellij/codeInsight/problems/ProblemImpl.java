package com.intellij.codeInsight.problems;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.problems.Problem;

/**
 * @author cdr
 */
public class ProblemImpl implements Problem {
  private VirtualFile virtualFile;
  public HighlightInfo highlightInfo;
  private boolean isSyntax;

  public ProblemImpl(final VirtualFile virtualFile, final HighlightInfo highlightInfo, final boolean isSyntax) {
    this.isSyntax = isSyntax;
    this.virtualFile = virtualFile;
    this.highlightInfo = highlightInfo;
  }

  public VirtualFile getVirtualFile() {
    return virtualFile;
  }

  public boolean isSyntaxOnly() {
    return isSyntax;
  }
}
