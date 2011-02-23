package com.intellij.structuralsearch;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;

/**
* @author Eugene.Kudelevsky
*/
public class FilteringMatchResultSink implements MatchResultSink {
  private final MatchResultSink mySink;

  public FilteringMatchResultSink(MatchResultSink sink) {
    mySink = sink;
  }

  public void newMatch(MatchResult result) {
    mySink.newMatch(result);
  }

  public void processFile(PsiFile element) {
    mySink.processFile(element);
  }

  public void setMatchingProcess(MatchingProcess matchingProcess) {
    mySink.setMatchingProcess(matchingProcess);
  }

  public void matchingFinished() {
    mySink.matchingFinished();
  }

  public ProgressIndicator getProgressIndicator() {
    return mySink.getProgressIndicator();
  }
}
