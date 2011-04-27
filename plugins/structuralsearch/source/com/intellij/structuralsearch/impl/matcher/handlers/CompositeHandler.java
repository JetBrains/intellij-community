package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class CompositeHandler extends MatchingHandler {
  private final MatchingHandler myDelegate;
  private MatchingHandler myLastUsedHandler;

  public CompositeHandler(MatchingHandler handler) {
    myDelegate = handler;
  }

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    myLastUsedHandler = myDelegate;
    if (myDelegate.match(patternNode, matchedNode, context)) {
      return true;
    }

    if (context.isWithAlternativePatternRoots()) {
      context.setWithAlternativePatternRoots(false);
      final List<PsiElement> alternativeElements = patternNode.getUserData(PatternCompiler.ALTERNATIVE_PATTERN_ROOTS);
      if (alternativeElements != null) {
        for (PsiElement alternativePatternNode : alternativeElements) {
          myLastUsedHandler = context.getPattern().getHandler(alternativePatternNode);
          if (myLastUsedHandler.match(alternativePatternNode, matchedNode, context)) {
            return true;
          }
        }
      }
      context.setWithAlternativePatternRoots(true);
    }
    return false;
  }

  @Override
  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    myLastUsedHandler = myDelegate;
    if (myDelegate.matchSequentially(nodes, nodes2, context)) {
      return true;
    }

    if (nodes.hasNext()) {
      final PsiElement first = nodes.current();
      final List<PsiElement> alternativeElements = first.getUserData(PatternCompiler.ALTERNATIVE_PATTERN_ROOTS);

      if (alternativeElements != null) {
        for (PsiElement alternativeNode : alternativeElements) {
          myLastUsedHandler = context.getPattern().getHandler(alternativeNode);
          if (myLastUsedHandler.matchSequentially(nodes, nodes2, context)) {
            return true;
          }
        }
      }
    }
     return false;
  }

  @Nullable
  public SubstitutionHandler findSubstitutionHandler(PsiElement element, MatchContext context) {
    if (myDelegate instanceof SubstitutionHandler) {
      return (SubstitutionHandler)myDelegate;
    }

    final List<PsiElement> alternativeElements = element.getUserData(PatternCompiler.ALTERNATIVE_PATTERN_ROOTS);
    if (alternativeElements != null) {
      for (PsiElement alternativeElement : alternativeElements) {
        MatchingHandler handler = context.getPattern().getHandlerSimple(alternativeElement);

        if (handler instanceof DelegatingHandler) {
          handler = ((DelegatingHandler)handler).getDelegate();
        }
        if (handler instanceof SubstitutionHandler) {
          return (SubstitutionHandler)handler;
        }
      }
    }

    return null;
  }

  @Override
  protected boolean isMatchSequentiallySucceeded(NodeIterator nodes2) {
    assert myLastUsedHandler != null;
    return myLastUsedHandler.isMatchSequentiallySucceeded(nodes2);
  }
}
