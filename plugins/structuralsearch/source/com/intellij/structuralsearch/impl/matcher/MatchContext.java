package com.intellij.structuralsearch.impl.matcher;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.psi.PsiElement;

import java.util.LinkedList;
import java.util.List;

/**
 * Global context of matching process
 */
public class MatchContext {
  private MatchResultSink sink;
  private LinkedList<MatchResultImpl> previousResults = new LinkedList<MatchResultImpl>();
  private MatchResultImpl result;
  private CompiledPattern pattern;
  private MatchOptions options;
  private MatchingVisitor matcher;

  public interface UnmatchedElementsListener {
    void matchedElements(List<PsiElement> elementList);
    void commitUnmatched();
  }

  private UnmatchedElementsListener unmatchedElementsListener;

  public void setMatcher(MatchingVisitor matcher) {
    this.matcher = matcher;
  }

  public MatchingVisitor getMatcher() {
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

  public void setPreviousResult(final MatchResultImpl previousResult) {
    if (previousResults.size() > 0) previousResults.removeLast();
    previousResults.addLast(previousResult);
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

  void setPattern(CompiledPattern pattern) {
    this.pattern = pattern;
  }

  MatchResultSink getSink() {
    return sink;
  }

  void setSink(MatchResultSink sink) {
    this.sink = sink;
  }

  void clear() {
    result = null;
    pattern = null;
  }

  void clearResult() {
    pattern.clearHandlersState();
    result.clear();
  }

  public void setUnmatchedElementsListener(UnmatchedElementsListener _unmatchedElementsListener) {
    unmatchedElementsListener = _unmatchedElementsListener;
  }

  public UnmatchedElementsListener getUnmatchedElementsListener() {
    return unmatchedElementsListener;
  }
}
