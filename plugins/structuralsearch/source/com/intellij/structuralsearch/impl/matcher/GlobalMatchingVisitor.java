package com.intellij.structuralsearch.impl.matcher;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceList;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.DelegatingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Visitor class to manage pattern matching
 */
@SuppressWarnings({"RefusedBequest"})
public class GlobalMatchingVisitor extends AbstractMatchingVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor");

  // the pattern element for visitor check
  private PsiElement myElement;

  // the result of matching in visitor
  private boolean myResult;

  // context of matching
  private MatchContext matchContext;

  //private final PsiElementVisitor myXmlVisitor = new XmlMatchingVisitor(this);
  //private final PsiElementVisitor myJavaVisitor = new JavaMatchingVisitor(this);

  private Map<Language, PsiElementVisitor> myLanguage2MatchingVisitor = new HashMap<Language, PsiElementVisitor>(1);

  /*private Language myLastLanguage;
  private PsiElementVisitor myLastVisitor;*/

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
    if ((elements == null && isLeftLooseMatching()) ||
        elements == elements2 // null
      ) {
      return true;
    }

    return matchInAnyOrder(
      elements.getReferenceElements(),
      (elements2 != null) ? elements2.getReferenceElements() : PsiElement.EMPTY_ARRAY
    );
  }

  @Override
  protected boolean doMatchInAnyOrder(NodeIterator elements, NodeIterator elements2) {
    return matchContext.getPattern().getHandler(elements.current()).matchInAnyOrder(
      elements,
      elements2,
      matchContext
    );
  }

  @NotNull
  @Override
  protected NodeFilter getNodeFilter() {
    return LexicalNodesFilter.getInstance();
  }

  public final boolean handleTypedElement(final PsiElement typedElement, final PsiElement match) {
    MatchingHandler handler = matchContext.getPattern().getHandler(typedElement);
    if (handler instanceof DelegatingHandler) {
      handler = ((DelegatingHandler)handler).getDelegate();
    }
    assert handler instanceof SubstitutionHandler : handler.getClass();
    return ((SubstitutionHandler)handler).handle(match, matchContext);
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
      /*if (el1 instanceof XmlElement) {
        el1.accept(myXmlVisitor);
      }
      else {
        el1.accept(myJavaVisitor);
      }*/
      PsiElementVisitor visitor = getVisitorForElement(el1);
      if (visitor != null) {
        el1.accept(visitor);
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

  @Nullable
  private PsiElementVisitor getVisitorForElement(PsiElement element) {
    Language language = element.getLanguage();
    PsiElementVisitor visitor = myLanguage2MatchingVisitor.get(language);
    if (visitor == null) {
      visitor = createMatchingVisitor(language);
      myLanguage2MatchingVisitor.put(language, visitor);
    }
    return visitor;
  }

  @Nullable
  private PsiElementVisitor createMatchingVisitor(Language language) {
    PsiElementVisitor visitor;
    StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(language);
    if (profile == null) {
      LOG.warn("there is no StructuralSearchProfile for language " + language.getID());
      return null;
    }
    else {
      visitor = profile.createMatchingVisitor(this);
      return visitor;
    }
  }

  public boolean shouldAdvanceThePattern(final PsiElement element, PsiElement match) {
    MatchingHandler handler = matchContext.getPattern().getHandler(element);

    return handler.shouldAdvanceThePatternFor(element, match);
  }

  // Matches tree segments starting with given elements to find equality
  // @param el1 the pattern element for matching
  // @param el2 the tree element for matching
  // @return if they are equal and false otherwise

  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2) {
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

  /**
   * Descents the tree in depth finding matches
   *
   * @param elements the element for which the sons are looked for match
   */
  public void matchContext(final NodeIterator elements) {
    final CompiledPattern pattern = matchContext.getPattern();
    final NodeIterator patternNodes = pattern.getNodes().clone();
    final MatchResultImpl saveResult = matchContext.hasResult() ? matchContext.getResult() : null;
    final List<PsiElement> saveMatchedNodes = matchContext.getMatchedNodes();

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

        final List<PsiElement> matchedNodes = matchContext.getMatchedNodes();

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

  public void setMatchContext(MatchContext matchContext) {
    this.matchContext = matchContext;
  }

  // Matches the sons of given elements to find equality
  // @param el1 the pattern element for matching
  // @param el2 the tree element for matching
  // @return if they are equal and false otherwise

  @Override
  protected boolean isLeftLooseMatching() {
    return matchContext.getOptions().isLooseMatching();
  }

  @Override
  protected boolean isRightLooseMatching() {
    return false;
  }
}
