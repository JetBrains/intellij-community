/**
 * @author Alexey
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;

import java.util.Collection;

/** @fabrique **/
public class HighlightInfoHolder extends SmartList<HighlightInfo>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder");

  private PsiFile myContextFile;
  private HighlightInfoFilter[] myFilters;
  private int myErrorCount;
  private int myWarningCount;
  private int myInfoCount;

  public HighlightInfoHolder(PsiFile contextFile, HighlightInfoFilter[] filters) {
    myContextFile = contextFile;
    myFilters = filters;
  }

  public boolean add(HighlightInfo info) {
    if (info == null || !accepted(info)) return false;

    HighlightSeverity severity = info.getSeverity();
    if (severity == HighlightSeverity.ERROR) {
      myErrorCount++;
    }
    else if (severity == HighlightSeverity.WARNING) {
      myWarningCount++;
    }
    else if (severity == HighlightSeverity.INFORMATION) {
      myInfoCount++;
    }

    return super.add(info);
  }

  private boolean accepted(HighlightInfo info) {
    for (HighlightInfoFilter filter : myFilters) {
      if (!filter.accept(info, myContextFile)) return false;
    }
    return true;
  }

  public void clear() {
    myErrorCount = 0;
    myWarningCount = 0;
    myInfoCount = 0;
    super.clear();
  }

  public boolean hasErrorResults() {
    return myErrorCount != 0;
  }

  public boolean hasInfoResults() {
    return myInfoCount != 0;
  }

  public boolean hasWarningResults() {
    return myWarningCount != 0;
  }

  public int getErrorCount() {
    return myErrorCount;
  }

  public boolean addAll(Collection<? extends HighlightInfo> highlightInfos) {
    if (highlightInfos == null) return false;
    LOG.assertTrue(highlightInfos != this);
    boolean added = false;
    for (final HighlightInfo highlightInfo : highlightInfos) {
      added |= add(highlightInfo);
    }
    return added;
  }
}