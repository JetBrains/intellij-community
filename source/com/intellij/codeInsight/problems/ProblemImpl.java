package com.intellij.codeInsight.problems;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.problems.Problem;
import org.jetbrains.annotations.NonNls;

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

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ProblemImpl problem = (ProblemImpl)o;

    if (isSyntax != problem.isSyntax) return false;
    if (!highlightInfo.equals(problem.highlightInfo)) return false;
    if (!virtualFile.equals(problem.virtualFile)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = virtualFile.hashCode();
    result = 31 * result + highlightInfo.hashCode();
    result = 31 * result + (isSyntax ? 1 : 0);
    return result;
  }

  @NonNls
  public String toString() {
    return "Problem: " + highlightInfo;
  }
}
