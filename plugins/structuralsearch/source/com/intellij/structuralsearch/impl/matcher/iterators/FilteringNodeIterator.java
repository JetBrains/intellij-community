package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;

/**
 * Iterator over important nodes
 */
public final class FilteringNodeIterator extends NodeIterator {
  private NodeIterator delegate;
  private final NodeFilter filter;

  private void advanceToNext() {
    while (delegate.hasNext() && filter.accepts(delegate.current())) {
      delegate.advance();
    }
  }

  private void rewindToPrevious() {
    while (filter.accepts(delegate.current())) {
      delegate.rewind();
    }
  }

  public FilteringNodeIterator(final PsiElement element) {
    this(new SiblingNodeIterator(element));
  }

  public FilteringNodeIterator(NodeIterator iterator, NodeFilter filter) {
    delegate = iterator;
    this.filter = filter;
    advanceToNext();
  }

  public FilteringNodeIterator(final NodeIterator iterator) {
    this(iterator, LexicalNodesFilter.getInstance());
  }

  public boolean hasNext() {
    return delegate.hasNext() && !filter.accepts(delegate.current());
  }

  public void rewind(int number) {
    while(number > 0) {
      delegate.rewind();
      rewindToPrevious();
      --number;
    }
  }

  public PsiElement current() {
    return delegate.current();
  }

  public void advance() {
    delegate.advance();
    advanceToNext();
  }

  public void rewind() {
    delegate.rewind();
    rewindToPrevious();
  }

  public void reset() {
    delegate.reset();
    advanceToNext();
  }
}
