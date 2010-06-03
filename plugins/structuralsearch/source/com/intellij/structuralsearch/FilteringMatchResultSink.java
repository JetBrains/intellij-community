package com.intellij.structuralsearch;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.impl.matcher.TokenBasedMatchResult;
import com.intellij.structuralsearch.impl.matcher.TokenBasedSearcher;

/**
* @author Eugene.Kudelevsky
*/
public class FilteringMatchResultSink implements MatchResultSink {
  private final MatchResultSink mySink;
  private final boolean myReplacement;

  public FilteringMatchResultSink(MatchResultSink sink, boolean replacement) {
    myReplacement = replacement;
    mySink = sink;
  }

  public void newMatch(MatchResult result) {
    if (result instanceof TokenBasedMatchResult) {
      if (TokenBasedSearcher.isApplicableResult((TokenBasedMatchResult)result, myReplacement)) {
        mySink.newMatch(result);
      }
    }
    else {
      mySink.newMatch(result);
    }
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
