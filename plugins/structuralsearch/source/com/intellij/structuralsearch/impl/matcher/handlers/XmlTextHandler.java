package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;

/**
 * Root of handlers for pattern node matching. Handles simpliest type of the match.
 */
public final class XmlTextHandler extends MatchingHandler {
  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    final PsiElement psiElement = nodes.current();

    return MatchingVisitor.continueMatchingSequentially(
      new FilteringNodeIterator( new ArrayBackedNodeIterator(psiElement.getChildren()) ),
      nodes2,
      context
    );
  }
}
