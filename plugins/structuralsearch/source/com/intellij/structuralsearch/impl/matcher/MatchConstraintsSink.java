package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.structuralsearch.MatchingProcess;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.progress.ProgressIndicator;

import javax.swing.*;
import java.util.HashMap;

/**
 * Sink to detect
 */
class MatchConstraintsSink implements MatchResultSink {
  private final MatchResultSink delegate;
  private MatchingProcess process;
  private final boolean distinct;
  private final boolean caseSensitive;
  private int matchCount;
  private final int maxMatches;
  private final HashMap<Object, MatchResult> matches = new HashMap<Object, MatchResult>();

  MatchConstraintsSink(MatchResultSink _delegate, int _maxMatches,boolean distinct, boolean _caseSensitive) {
    delegate = _delegate;
    maxMatches = _maxMatches;
    this.distinct = distinct;
    caseSensitive = _caseSensitive;
  }

  public void newMatch(MatchResult result) {
    if (distinct) {
      String matchImage = result.getMatchImage();

      if (!caseSensitive) matchImage = matchImage.toLowerCase();

      if (matches.get(matchImage)!=null) {
        return;
      }

      matches.put(matchImage,result);
    }
    else {
      final PsiElement match = result.getMatch();
      if (matches.containsKey(match)) {
        return;
      }
      matches.put(match, result);
    }

    delegate.newMatch(result);
    ++matchCount;

    if (matchCount==maxMatches) {
      JOptionPane.showMessageDialog(null, SSRBundle.message("search.produced.too.many.results.message"));
      process.stop();
    }
  }

  /* Notifies sink about starting the matching for given element
   * @param element the current file
   * @param task process continuation reference
   */
  public void processFile(PsiFile element) {
    delegate.processFile(element);
  }

  public void setMatchingProcess(MatchingProcess matchingProcess) {
    process = matchingProcess;
    delegate.setMatchingProcess(process);
  }

  public void matchingFinished() {
    matchCount = 0;
    matches.clear();
    delegate.matchingFinished();
  }

  public ProgressIndicator getProgressIndicator() {
    return delegate.getProgressIndicator();
  }

}
