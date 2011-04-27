package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResultSink;

import java.util.LinkedList;
import java.util.List;

/**
 * Global context of matching process
 */
public class MatchContext {
  private MatchResultSink sink;
  private final LinkedList<MatchResultImpl> previousResults = new LinkedList<MatchResultImpl>();
  private MatchResultImpl result;
  private CompiledPattern pattern;
  private MatchOptions options;
  private GlobalMatchingVisitor matcher;
  private boolean shouldRecursivelyMatch = true;
  private boolean myWithAlternativePatternRoots = true;

  private List<PsiElement> myMatchedNodes;

  public List<PsiElement> getMatchedNodes() {
    return myMatchedNodes;
  }

  public void setMatchedNodes(final List<PsiElement> matchedNodes) {
    myMatchedNodes = matchedNodes;
  }

  public boolean isWithAlternativePatternRoots() {
    return myWithAlternativePatternRoots;
  }

  public void setWithAlternativePatternRoots(boolean withAlternativePatternRoots) {
    myWithAlternativePatternRoots = withAlternativePatternRoots;
  }

  public interface UnmatchedElementsListener {
    void matchedElements(List<PsiElement> elementList);
    void commitUnmatched();
  }

  private UnmatchedElementsListener unmatchedElementsListener;

  public void setMatcher(GlobalMatchingVisitor matcher) {
    this.matcher = matcher;
  }

  public GlobalMatchingVisitor getMatcher() {
    return matcher;
  }

  public MatchOptions getOptions() {
    return options;
  }

  public void setOptions(MatchOptions options) {
    this.options = options;
  }

  public MatchResultImpl getPreviousResult() {
    return previousResults.size() == 0 ? null:previousResults.getLast();
  }

  public MatchResultImpl getResult() {
    if (result==null) result = new MatchResultImpl();
    return result;
  }

  public void pushResult() {
    previousResults.addLast(result);
    result = null;
  }
  
  public void popResult() {
    result = previousResults.removeLast();
  }
  
  public void setResult(MatchResultImpl result) {
    this.result = result;
    if (result == null) {
      pattern.clearHandlersState();
    }
  }

  public boolean hasResult() {
    return result!=null;
  }

  public CompiledPattern getPattern() {
    return pattern;
  }

  public void setPattern(CompiledPattern pattern) {
    this.pattern = pattern;
  }

  public MatchResultSink getSink() {
    return sink;
  }

  public void setSink(MatchResultSink sink) {
    this.sink = sink;
  }

  void clear() {
    result = null;
    pattern = null;
  }

  public boolean shouldRecursivelyMatch() {
    return shouldRecursivelyMatch;
  }

  public void setShouldRecursivelyMatch(boolean shouldRecursivelyMatch) {
    this.shouldRecursivelyMatch = shouldRecursivelyMatch;
  }

  public void setUnmatchedElementsListener(UnmatchedElementsListener _unmatchedElementsListener) {
    unmatchedElementsListener = _unmatchedElementsListener;
  }

  public UnmatchedElementsListener getUnmatchedElementsListener() {
    return unmatchedElementsListener;
  }
}
