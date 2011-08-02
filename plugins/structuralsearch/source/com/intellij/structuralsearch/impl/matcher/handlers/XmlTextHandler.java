package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;

/**
 * Root of handlers for pattern node matching. Handles simpliest type of the match.
 */
public final class XmlTextHandler extends MatchingHandler {
  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    final PsiElement psiElement = nodes.current();

    return GlobalMatchingVisitor.continueMatchingSequentially(
      new SsrFilteringNodeIterator( new ArrayBackedNodeIterator(psiElement.getChildren()) ),
      nodes2,
      context
    );
  }
}
