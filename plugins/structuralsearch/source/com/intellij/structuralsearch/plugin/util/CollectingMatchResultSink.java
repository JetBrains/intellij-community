package com.intellij.structuralsearch.plugin.util;

import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.MatchingProcess;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.progress.ProgressIndicator;

import java.util.List;
import java.util.LinkedList;

public class CollectingMatchResultSink implements MatchResultSink {
  private List<MatchResult> matches = new LinkedList<MatchResult>();

  public void newMatch(MatchResult result) {
    matches.add(result);
  }

  /* Notifies sink about starting the matching for given element
   * @param element the current file
   */
  public void processFile(PsiFile element) {
  }

  public void matchingFinished() {
  }

  public ProgressIndicator getProgressIndicator() {
    return null;
  }

  public void setMatchingProcess(MatchingProcess process) {
  }

  public List<MatchResult> getMatches() {
    return matches;
  }
}