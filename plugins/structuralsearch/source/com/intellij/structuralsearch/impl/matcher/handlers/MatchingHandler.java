package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.filters.DefaultFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.BinaryPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;

import java.util.LinkedList;
import java.util.List;

/**
 * Root of handlers for pattern node matching. Handles simpliest type of the match.
 */
public abstract class MatchingHandler extends MatchPredicate {
  protected NodeFilter filter;
  private PsiElement pinnedElement;

  public void setFilter(NodeFilter filter) {
    this.filter = filter;
  }

  /**
   * Matches given handler node against given value.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successfull and false otherwise
   */
  public boolean match(PsiElement patternNode,PsiElement matchedNode, int start, int end, MatchContext context) {
    return match(patternNode,matchedNode,context);
  }

  /**
   * Matches given handler node against given value.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successfull and false otherwise
   */
  public boolean match(PsiElement patternNode,PsiElement matchedNode, MatchContext context) {
    if (patternNode == null) {
      return matchedNode == null;
    }

    return canMatch(patternNode, matchedNode);
  }

  public boolean canMatch(final PsiElement patternNode, final PsiElement matchedNode) {
    if (filter!=null) {
      if (!filter.accepts(matchedNode)) return false;
      return true;
    } else {
      return DefaultFilter.accepts(patternNode, matchedNode);
    }
  }

  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    PsiElement patternElement;
    MatchingHandler handler;
    MatchingStrategy strategy = context.getPattern().getStrategy();

    skipIfNeccessary(nodes, nodes2, strategy);
    skipIfNeccessary(nodes2, nodes, strategy);

    if (nodes2.hasNext() &&
        (handler = context.getPattern().getHandler(nodes.current())).match(patternElement = nodes.current(),nodes2.current(),context)) {

      nodes.advance();

      if (shouldAdvanceTheMatchFor(patternElement, nodes2.current())) {
        nodes2.advance();
        skipIfNeccessary(nodes, nodes2, strategy);
      }
      skipIfNeccessary(nodes2, nodes, strategy);

      if (nodes.hasNext()) {
        final MatchingHandler nextHandler = context.getPattern().getHandler(nodes.current());

        if (nextHandler.matchSequentially(nodes,nodes2,context)) {
          // match was found!
          return true;
        } else {
          // rewind, we was not able to match descendants
          nodes.rewind();
          nodes2.rewind();
        }
      } else {
        // match was found
        return handler.isMatchSequentiallySucceeded(nodes2);
      }
    }
    return false;
  }

  private static void skipIfNeccessary(NodeIterator nodes, NodeIterator nodes2, MatchingStrategy strategy) {
    while (strategy.shouldSkip(nodes2.current(), nodes.current())) {
      nodes2.advance();
    }
  }

  protected boolean isMatchSequentiallySucceeded(final NodeIterator nodes2) {
    return !nodes2.hasNext();
  }

  private static MatchPredicate findRegExpPredicate(MatchPredicate start) {
    if (start==null) return null;
    if (start instanceof RegExpPredicate) return start;

    if(start instanceof BinaryPredicate) {
      BinaryPredicate binary = (BinaryPredicate)start;
      final MatchPredicate result = findRegExpPredicate(binary.getFirst());
      if (result!=null) return result;

      return findRegExpPredicate(binary.getSecond());
    } else if (start instanceof NotPredicate) {
      return null;
    }
    return null;
  }

  public static RegExpPredicate getSimpleRegExpPredicate(SubstitutionHandler handler) {
    if (handler == null) return null;
    return (RegExpPredicate)findRegExpPredicate(handler.getPredicate());
  }

  static class ClearStateVisitor extends PsiRecursiveElementWalkingVisitor {
    private CompiledPattern pattern;

    ClearStateVisitor() {
      super(true);
    }

    @Override public void visitElement(PsiElement element) {
      // We do not reset certain handlers because they are also bound to higher level nodes
      // e.g. Identifier handler in name is also bound to PsiMethod
      if (!(element instanceof PsiJavaToken) &&
          (!(element instanceof PsiJavaCodeReferenceElement) ||
           !(element.getParent() instanceof PsiAnnotation))
         ) {
        MatchingHandler handler = pattern.getHandlerSimple(element);
        if (handler instanceof SubstitutionHandler) {
          handler.reset();
        }
      }
      super.visitElement(element);
    }

    synchronized void clearState(CompiledPattern _pattern, PsiElement el) {
      pattern = _pattern;
      el.acceptChildren(this);
      pattern = null;
    }
  }

  protected static ClearStateVisitor clearingVisitor = new ClearStateVisitor();

  public boolean matchInAnyOrder(NodeIterator nodes, NodeIterator nodes2, final MatchContext context) {
    MatchResultImpl saveResult = context.hasResult()?context.getResult():null;
    context.setResult(null);

    try {

      if (nodes.hasNext() && !nodes2.hasNext()) {
        return validateSatisfactionOfHandlers(nodes, context);
      }

      List<PsiElement> usedVars = null;

      for(;nodes.hasNext();nodes.advance()) {
        final PsiElement el = nodes.current();

        final PsiElement startMatching = nodes2.current();
        do {
          final MatchingHandler handler = context.getPattern().getHandler(el);
          final PsiElement element = handler.getPinnedNode(null);
          final PsiElement el2 = element != null ? element:nodes2.current();

          if (element == null) nodes2.advance();
          if (!nodes2.hasNext()) nodes2.reset();

          if (usedVars== null ||
              usedVars.indexOf(el2)==-1) {
            final boolean matched = handler.match(el,el2,context);

            if (matched) {
              if (usedVars==null) usedVars = new LinkedList<PsiElement>();
              usedVars.add(el2);
              if (context.getMatcher().shouldAdvanceThePattern(el, el2)) {
                break;
              }
            } else if (element != null) {
              return false;
            }

            // clear state of dependent objects
            clearingVisitor.clearState(context.getPattern(),el);
          }

          // passed of elements and does not found the match
          if (startMatching == nodes2.current()) {
            boolean result = validateSatisfactionOfHandlers(nodes,context);
            if (result && context.getUnmatchedElementsListener() != null) {
              context.getUnmatchedElementsListener().matchedElements(usedVars);
            }
            return result;
          }
        } while(true);

        if (!context.getMatcher().shouldAdvanceThePattern(el, null)) {
          nodes.rewind();
        }
      }

      boolean result = validateSatisfactionOfHandlers(nodes,context);
      if (result && context.getUnmatchedElementsListener()!=null) {
        context.getUnmatchedElementsListener().matchedElements(usedVars);
      }
      return result;
    } finally {
      if (saveResult!=null) {
        if (context.hasResult()) {
          saveResult.getMatches().addAll(context.getResult().getMatches());
        }
        context.setResult(saveResult);
      }
    }
  }

  private static boolean validateSatisfactionOfHandlers(NodeIterator nodes, MatchContext context) {

    while(nodes.hasNext()) {
      final PsiElement element = nodes.current();
      final MatchingHandler handler = context.getPattern().getHandler( element );

      if (handler instanceof SubstitutionHandler) {
        if (!((SubstitutionHandler)handler).validate(context,SubstitutionHandler.getElementContextByPsi(element))) {
          return false;
        }
      } else {
        return false;
      }
      nodes.advance();
    }
    return true;
  }

  public NodeFilter getFilter() {
    return filter;
  }

  public boolean shouldAdvanceThePatternFor(PsiElement patternElement, PsiElement matchedElement) {
    return true;
  }

  public boolean shouldAdvanceTheMatchFor(PsiElement patternElement, PsiElement matchedElement) {
    return true;
  }

  public void reset() {
    //pinnedElement = null;
  }

  public PsiElement getPinnedNode(PsiElement context) {
    return pinnedElement;
  }

  public void setPinnedElement(final PsiElement pinnedElement) {
    this.pinnedElement = pinnedElement;
  }
}