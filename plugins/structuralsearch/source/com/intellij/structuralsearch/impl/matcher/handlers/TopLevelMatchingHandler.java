package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.SiblingNodeIterator;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public final class TopLevelMatchingHandler extends MatchingHandler implements DelegatingHandler {
  private final MatchingHandler delegate;

  public TopLevelMatchingHandler(@NotNull MatchingHandler _delegate) {
    delegate = _delegate;
    if (_delegate instanceof ExpressionHandler) {
      setFilter(_delegate.getFilter());
    }
  }

  public boolean match(final PsiElement patternNode, final PsiElement matchedNode, final MatchContext matchContext) {
    final boolean matched = delegate.match(patternNode, matchedNode, matchContext);

    if (matched) {
      List<PsiElement> matchedNodes = matchContext.getMatchedNodes();
      if (matchedNodes == null) {
        matchedNodes = new LinkedList<PsiElement>();
        matchContext.setMatchedNodes(matchedNodes);
      }

      PsiElement elementToAdd = matchedNode;

      if (patternNode instanceof PsiComment && matchedNode instanceof PsiMember
         ) {
        // psicomment and psidoccomment are placed inside the psimember next to them so
        // simple topdown matching should do additional "dances" to cover this case.
        elementToAdd = matchedNode.getFirstChild();
        assert elementToAdd instanceof PsiComment;
      }

      matchedNodes.add(elementToAdd);
    } else {
      //if (matchContext.hasResult()) matchContext.clearResult();
    }

    if ((!matched || matchContext.getOptions().isRecursiveSearch()) &&
        matchContext.getPattern().getStrategy().continueMatching(matchedNode) &&
        matchContext.shouldRecursivelyMatch()
       ) {
      matchContext.getMatcher().matchContext(
        new FilteringNodeIterator(
          new SiblingNodeIterator(matchedNode.getFirstChild())
        )
      );
    }
    return matched;
  }

  @Override
  public boolean matchSequentially(final NodeIterator nodes, final NodeIterator nodes2, final MatchContext context) {
    return delegate.matchSequentially(nodes, nodes2, context);
  }

  public boolean match(final PsiElement patternNode,
                       final PsiElement matchedNode, final int start, final int end, final MatchContext context) {
    return match(patternNode, matchedNode, context);
  }

  public boolean isMatchSequentiallySucceeded(final NodeIterator nodes2) {
    return true;
  }

  @Override
  public boolean shouldAdvanceTheMatchFor(final PsiElement patternElement, final PsiElement matchedElement) {
    return delegate.shouldAdvanceTheMatchFor(patternElement, matchedElement);
  }

  public MatchingHandler getDelegate() {
    return delegate;
  }
}