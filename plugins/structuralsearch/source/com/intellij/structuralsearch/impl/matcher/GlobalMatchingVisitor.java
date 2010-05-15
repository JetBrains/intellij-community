package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.xml.XmlElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Visitor class to manage pattern matching
 */
@SuppressWarnings({"RefusedBequest"})
public class GlobalMatchingVisitor {

  // the pattern element for visitor check
  private PsiElement myElement;
  
  // the result of matching in visitor
  private boolean myResult;

  // context of matching
  private MatchContext matchContext;

  private final PsiElementVisitor myXmlVisitor = new XmlMatchingVisitor(this);
  private final PsiElementVisitor myJavaVisitor = new JavaMatchingVisitor(this);

  public static final String[] MODIFIERS = {
    PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC, PsiModifier.ABSTRACT, PsiModifier.FINAL,
    PsiModifier.NATIVE, PsiModifier.SYNCHRONIZED, PsiModifier.STRICTFP, PsiModifier.TRANSIENT, PsiModifier.VOLATILE
  };

  static {
    Arrays.sort(MODIFIERS);
  }

  public PsiElement getElement() {
    return myElement;
  }

  public boolean getResult() {
    return myResult;
  }

  public void setElement(PsiElement element) {
    this.myElement = element;
  }

  public void setResult(boolean result) {
    this.myResult = result;
  }

  public MatchContext getMatchContext() {
    return matchContext;
  }

  protected boolean matchInAnyOrder(final PsiReferenceList elements, final PsiReferenceList elements2) {
    if ((elements == null && matchContext.getOptions().isLooseMatching()) ||
        elements == elements2 // null
      ) {
      return true;
    }

    return matchInAnyOrder(
      elements.getReferenceElements(),
      (elements2 != null) ? elements2.getReferenceElements() : PsiElement.EMPTY_ARRAY
    );
  }

  protected final boolean matchInAnyOrder(final PsiElement[] elements, final PsiElement[] elements2) {
    if (elements == elements2) return true;

    return matchInAnyOrder(
      new ArrayBackedNodeIterator(elements),
      new ArrayBackedNodeIterator(elements2)
    );
  }

  protected final boolean matchInAnyOrder(final NodeIterator elements, final NodeIterator elements2) {
    if ((!elements.hasNext() && matchContext.getOptions().isLooseMatching()) ||
        (!elements.hasNext() && !elements2.hasNext())
      ) {
      return true;
    }

    return matchContext.getPattern().getHandler(elements.current()).matchInAnyOrder(
      elements,
      elements2,
      matchContext
    );
  }

  protected final boolean handleTypedElement(final PsiElement typedElement, final PsiElement match) {
    final SubstitutionHandler handler = (SubstitutionHandler)matchContext.getPattern().getHandler(typedElement);
    return handler.handle(match, matchContext);
  }

  /**
   * Identifies the match between given element of program tree and pattern element
   *
   * @param el1 the pattern for matching
   * @param el2 the tree element for matching
   * @return true if equal and false otherwise
   */
  public boolean match(final PsiElement el1, final PsiElement el2) {
    // null
    if (el1 == el2) return true;
    if (el2 == null || el1 == null) {
      // this a bug!
      return false;
    }

    // copy changed data to local stack
    PsiElement prevElement = myElement;
    myElement = el2;

    try {
      if (el1 instanceof XmlElement) {
        el1.accept(myXmlVisitor);
      }
      else {
        el1.accept(myJavaVisitor);
      }
    }
    catch (ClassCastException ex) {
      myResult = false;
    }
    finally {
      myElement = prevElement;
    }

    return myResult;
  }

  public boolean shouldAdvanceThePattern(final PsiElement element, PsiElement match) {
    MatchingHandler handler = matchContext.getPattern().getHandler(element);

    return handler.shouldAdvanceThePatternFor(element, match);
  }

  // Matches tree segments starting with given elements to find equality
  // @param el1 the pattern element for matching
  // @param el2 the tree element for matching
  // @return if they are equal and false otherwise

  protected boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2) {
    return continueMatchingSequentially(nodes, nodes2, matchContext);
  }

  public static boolean continueMatchingSequentially(final NodeIterator nodes, final NodeIterator nodes2, MatchContext matchContext) {
    if (!nodes.hasNext()) {
      return nodes.hasNext() == nodes2.hasNext();
    }

    return matchContext.getPattern().getHandler(nodes.current()).matchSequentially(
      nodes,
      nodes2,
      matchContext
    );
  }

  // Matches tree segments starting with given elements to find equality
  // @param el1 the pattern element for matching
  // @param el2 the tree element for matching
  // @return if they are equal and false otherwise

  protected boolean matchSequentially(PsiElement el1, PsiElement el2) {
    //if (el1==null || el2==null) return el1 == el2;
    return matchSequentially(new FilteringNodeIterator(el1), new FilteringNodeIterator(el2));
  }

  /**
   * Descents the tree in depth finding matches
   *
   * @param elements the element for which the sons are looked for match
   */
  public void matchContext(final NodeIterator elements) {
    final CompiledPattern pattern = matchContext.getPattern();
    final NodeIterator patternNodes = pattern.getNodes().clone();
    final MatchResultImpl saveResult = matchContext.hasResult() ? matchContext.getResult() : null;
    final LinkedList<PsiElement> saveMatchedNodes = matchContext.getMatchedNodes();

    try {
      matchContext.setResult(null);
      matchContext.setMatchedNodes(null);

      if (!patternNodes.hasNext()) return;
      final MatchingHandler firstMatchingHandler = pattern.getHandler(patternNodes.current());

      for (; elements.hasNext(); elements.advance()) {
        final PsiElement elementNode = elements.current();

        boolean matched =
          firstMatchingHandler.matchSequentially(patternNodes, elements, matchContext);

        if (matched) {
          MatchingHandler matchingHandler = matchContext.getPattern().getHandler(Configuration.CONTEXT_VAR_NAME);
          if (matchingHandler != null) {
            matched = ((SubstitutionHandler)matchingHandler).handle(elementNode, matchContext);
          }
        }

        final LinkedList<PsiElement> matchedNodes = matchContext.getMatchedNodes();

        if (matched) {
          dispatchMatched(matchedNodes, matchContext.getResult());
        }

        matchContext.setMatchedNodes(null);
        matchContext.setResult(null);

        patternNodes.reset();
        if (matchedNodes != null && matchedNodes.size() > 0 && matched) {
          elements.rewind();
        }
      }
    }
    finally {
      matchContext.setResult(saveResult);
      matchContext.setMatchedNodes(saveMatchedNodes);
    }
  }

  private void dispatchMatched(final List<PsiElement> matchedNodes, MatchResultImpl result) {
    if (!matchContext.getOptions().isResultIsContextMatch() && doDispatch(result, result)) return;

    // There is no substitutions so show the context

    processNoSubstitutionMatch(matchedNodes, result);
    matchContext.getSink().newMatch(result);
  }

  private boolean doDispatch(final MatchResultImpl result, MatchResultImpl context) {
    boolean ret = false;

    for (MatchResult _r : result.getAllSons()) {
      final MatchResultImpl r = (MatchResultImpl)_r;

      if ((r.isScopeMatch() && !r.isTarget()) || r.isMultipleMatch()) {
        ret |= doDispatch(r, context);
      }
      else if (r.isTarget()) {
        r.setContext(context);
        matchContext.getSink().newMatch(r);
        ret = true;
      }
    }
    return ret;
  }

  private static void processNoSubstitutionMatch(List<PsiElement> matchedNodes, MatchResultImpl result) {
    boolean complexMatch = matchedNodes.size() > 1;
    final PsiElement match = matchedNodes.get(0);

    if (!complexMatch) {
      result.setMatchRef(new SmartPsiPointer(match));
      result.setMatchImage(match.getText());
    }
    else {
      MatchResultImpl sonresult;

      for (final PsiElement matchStatement : matchedNodes) {
        result.getMatches().add(
          sonresult = new MatchResultImpl(
            MatchResult.LINE_MATCH,
            matchStatement.getText(),
            new SmartPsiPointer(matchStatement),
            true
          )
        );

        sonresult.setParent(result);
      }

      result.setMatchRef(
        new SmartPsiPointer(match)
      );
      result.setMatchImage(
        match.getText()
      );
      result.setName(MatchResult.MULTI_LINE_MATCH);
    }
  }

  void setMatchContext(MatchContext matchContext) {
    this.matchContext = matchContext;
  }

}
