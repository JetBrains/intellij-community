package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.filters.DefaultFilter;

import java.util.List;
import java.util.LinkedList;

/**
 * Root of handlers for pattern node matching. Handles simpliest type of the match.
 */
public abstract class Handler {
  protected NodeFilter filter;

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
    
    if (filter!=null) {
      if (!filter.accepts(matchedNode)) return false;
      return true;
    } else {
      return DefaultFilter.accepts(patternNode,matchedNode);
    }
  }

  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    if (nodes2.hasNext() && match(nodes.current(),nodes2.current(),context)) {
      nodes2.advance();
      nodes.advance();

      if (nodes.hasNext()) {
        final Handler nextHandler = context.getPattern().getHandler(nodes.current());

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
        return !nodes2.hasNext();
      }
    }
    return false;
  }

  public static void setUnmatchedElementsListener(UnmatchedElementsListener _unmatchedElementsListener) {
    unmatchedElementsListener = _unmatchedElementsListener;
  }

  public static UnmatchedElementsListener getUnmatchedElementsListener() {
    return unmatchedElementsListener;
  }

  static class ClearStateVisitor extends PsiRecursiveElementVisitor {
    private CompiledPattern pattern;

    public void visitElement(PsiElement element) {
      if (!(element instanceof PsiJavaToken)) {
        Handler handler = pattern.getHandlerSimple(element);
        if (handler instanceof SubstitutionHandler) {
          ((SubstitutionHandler)handler).reset();
        }
      }
      super.visitElement(element);
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }

    void clearState(CompiledPattern _pattern, PsiElement el) {
      pattern = _pattern;
      el.acceptChildren(this);
      pattern = null;
    }
  }

  protected static ClearStateVisitor clearingVisitor = new ClearStateVisitor();
  public static interface UnmatchedElementsListener {
    void matchedElements(List<PsiElement> elementList);
    void commitUnmatched();
  }

  private static UnmatchedElementsListener unmatchedElementsListener;

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
          final PsiElement el2 = nodes2.current();

          nodes2.advance();
          if (!nodes2.hasNext()) nodes2.reset();

          if (usedVars== null ||
              usedVars.indexOf(el2)==-1) {
            final Handler handler = context.getPattern().getHandler(el);
            final boolean matched = handler.match(el,el2,context);

            if (matched) {
              if (usedVars==null) usedVars = new LinkedList<PsiElement>();
              usedVars.add(el2);
              if (context.getMatcher().shouldAdvanceThePattern(el, el2)) {
                break;
              }
            }

            // clear state of dependent objects
            clearingVisitor.clearState(context.getPattern(),el);
          }

          // passed of elements and does not found the match
          if (startMatching == nodes2.current()) {
            boolean result = validateSatisfactionOfHandlers(nodes,context);
            if (result && unmatchedElementsListener!=null) {
              unmatchedElementsListener.matchedElements(usedVars);
            }
            return result;
          }
        } while(true);

        if (!context.getMatcher().shouldAdvanceThePattern(el, null)) {
          nodes.rewind();
        }
      }

      boolean result = validateSatisfactionOfHandlers(nodes,context);
      if (result && unmatchedElementsListener!=null) {
        unmatchedElementsListener.matchedElements(usedVars);
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
      final Handler handler = context.getPattern().getHandler( element );

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
}
