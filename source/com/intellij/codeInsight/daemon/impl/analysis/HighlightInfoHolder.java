/**
 * @author Alexey
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;

import java.util.Collection;
import java.util.Iterator;

/** @fabrique **/
public class HighlightInfoHolder extends SmartList<HighlightInfo>{
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

    final HighlightInfo.Severity severity = info.getSeverity();
    if (severity == HighlightInfo.ERROR) {
      myErrorCount++;
    }
    else if (severity == HighlightInfo.WARNING) {
      myWarningCount++;
    }
    else if (severity == HighlightInfo.INFORMATION) {
      myInfoCount++;
    }

    return super.add(info);
  }

  private boolean accepted(HighlightInfo info) {
    for (int i = 0; i < myFilters.length; i++) {
      HighlightInfoFilter filter = myFilters[i];
      if (!filter.accept(info.type, myContextFile)) return false;
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
    boolean added = false;
    for (Iterator<? extends HighlightInfo> iterator = highlightInfos.iterator(); iterator.hasNext();) {
      final HighlightInfo highlightInfo = iterator.next();
      added |= add(highlightInfo);
    }
    return added;
  }
}