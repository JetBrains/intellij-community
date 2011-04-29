package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.filters.CompositeFilter;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.LiteralWithSubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.impl.matcher.strategies.ExprMatchingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.structuralsearch.MatchOptions.INSTANCE_MODIFIER_NAME;
import static com.intellij.structuralsearch.MatchOptions.MODIFIER_ANNOTATION_NAME;
import static com.intellij.structuralsearch.MatchOptions.PACKAGE_LOCAL_MODIFIER_NAME;

/**
 * @author maxim
 */
public class GlobalCompilingVisitor {
  @NonNls private static final String SUBSTITUTION_PATTERN_STR = "\\b(__\\$_\\w+)\\b";
  private static Pattern ourSubstitutionPattern = Pattern.compile(SUBSTITUTION_PATTERN_STR);
  private static final Set<String> ourReservedWords = new HashSet<String>(
    Arrays.asList(MODIFIER_ANNOTATION_NAME, INSTANCE_MODIFIER_NAME, PACKAGE_LOCAL_MODIFIER_NAME)
  );
  private static final Pattern ourAlternativePattern = Pattern.compile("^\\((.+)\\)$");
  @NonNls private static final String WORD_SEARCH_PATTERN_STR = ".*?\\b(.+?)\\b.*?";
  private static final Pattern ourWordSearchPattern = Pattern.compile(WORD_SEARCH_PATTERN_STR);
  private CompileContext context;
  private final ArrayList<PsiElement> myLexicalNodes = new ArrayList<PsiElement>();

  //private final JavaCompilingVisitor myJavaVisitor = new JavaCompilingVisitor(this);
  //private final PsiElementVisitor myXmlVisitor = new XmlCompilingVisitor(this);

  private int myCodeBlockLevel;

  private static final NodeFilter ourFilter = LexicalNodesFilter.getInstance();

  public static NodeFilter getFilter() {
    return ourFilter;
  }

  public void setHandler(PsiElement element, MatchingHandler handler) {
    MatchingHandler realHandler = context.getPattern().getHandlerSimple(element);

    if (realHandler instanceof SubstitutionHandler) {
      ((SubstitutionHandler)realHandler).setMatchHandler(handler);
    }
    else {
      // @todo care about composite handler in this case of simple handler!
      context.getPattern().setHandler(element, handler);
    }
  }

  public final void handle(PsiElement element) {

    if ((!ourFilter.accepts(element) ||
         element instanceof PsiIdentifier) &&
        (context.getPattern().isRealTypedVar(element)) &&
        context.getPattern().getHandlerSimple(element) == null
      ) {
      String name = context.getPattern().getTypedVarString(element);
      // name is the same for named element (clazz,methods, etc) and token (name of ... itself)
      // @todo need fix this
      final SubstitutionHandler handler;

      context.getPattern().setHandler(
        element,
        handler = (SubstitutionHandler)context.getPattern().getHandler(name)
      );

      if (handler != null && context.getOptions().getVariableConstraint(handler.getName()).isPartOfSearchResults()) {
        handler.setTarget(true);
        context.getPattern().setTargetNode(element);
      }
    }
  }

  public CompileContext getContext() {
    return context;
  }

  public int getCodeBlockLevel() {
    return myCodeBlockLevel;
  }

  public void setCodeBlockLevel(int codeBlockLevel) {
    this.myCodeBlockLevel = codeBlockLevel;
  }

  static void setFilter(MatchingHandler handler, NodeFilter filter) {
    if (handler.getFilter() != null &&
        handler.getFilter().getClass() != filter.getClass()
      ) {
      // for constructor we will have the same handler for class and method and tokens itselfa
      handler.setFilter(
        new CompositeFilter(
          filter,
          handler.getFilter()
        )
      );
    }
    else {
      handler.setFilter(filter);
    }
  }

  public ArrayList<PsiElement> getLexicalNodes() {
    return myLexicalNodes;
  }

  public void addLexicalNode(PsiElement node) {
    myLexicalNodes.add(node);
  }

  void compile(PsiElement[] elements, CompileContext context) {
    myCodeBlockLevel = 0;
    this.context = context;
    /*if (element instanceof XmlElement) {
      element.accept(myXmlVisitor);
    }
    else {
      element.accept(myJavaVisitor);
    }*/
    StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(elements[0]);
    assert profile != null;
    profile.compile(elements, this);

    if (context.getPattern().getStrategy() == null) {
      context.getPattern().setStrategy(ExprMatchingStrategy.getInstance());
    }
  }

  @Nullable
  public MatchingHandler processPatternStringWithFragments(String pattern, OccurenceKind kind) {
    return processPatternStringWithFragments(pattern, kind, ourSubstitutionPattern);
  }

  @Nullable
  public MatchingHandler processPatternStringWithFragments(String pattern, OccurenceKind kind, Pattern substitutionPattern) {
    String content;

    if (kind == OccurenceKind.LITERAL) {
      content = pattern.substring(1, pattern.length() - 1);
    }
    else if (kind == OccurenceKind.COMMENT) {
      content = pattern;
    }
    else {
      return null;
    }

    StringBuffer buf = new StringBuffer(content.length());
    Matcher matcher = substitutionPattern.matcher(content);
    List<SubstitutionHandler> handlers = null;
    int start = 0;
    String word;
    boolean hasLiteralContent = false;

    SubstitutionHandler handler = null;
    while (matcher.find()) {
      if (handlers == null) handlers = new LinkedList<SubstitutionHandler>();
      handler = (SubstitutionHandler)getContext().getPattern().getHandler(matcher.group(1));
      if (handler != null) handlers.add(handler);

      word = content.substring(start, matcher.start());

      if (word.length() > 0) {
        buf.append(StructuralSearchUtil.shieldSpecialChars(word));
        hasLiteralContent = true;

        processTokenizedName(word, false, kind);
      }

      RegExpPredicate predicate = MatchingHandler.getSimpleRegExpPredicate(handler);

      if (predicate == null || !predicate.isWholeWords()) {
        buf.append("(.*?)");
      }
      else {
        buf.append(".*?\\b(").append(predicate.getRegExp()).append(")\\b.*?");
      }

      if (!IsNotSuitablePredicate(predicate, handler)) {
        processTokenizedName(predicate.getRegExp(), false, kind);
      }

      start = matcher.end();
    }

    word = content.substring(start, content.length());

    if (word.length() > 0) {
      hasLiteralContent = true;
      buf.append(StructuralSearchUtil.shieldSpecialChars(word));

      processTokenizedName(word, false, kind);
    }

    if (hasLiteralContent) {
      if (kind == OccurenceKind.LITERAL) {
        buf.insert(0, "\"");
        buf.append("\"");
      }
      buf.append("$");
    }

    if (handlers != null) {
      return (hasLiteralContent) ? (MatchingHandler)new LiteralWithSubstitutionHandler(
        buf.toString(),
        handlers
      ) :
             handler;
    }

    return null;
  }

  static boolean IsNotSuitablePredicate(RegExpPredicate predicate, SubstitutionHandler handler) {
    return predicate == null || handler.getMinOccurs() == 0 || !predicate.couldBeOptimized();
  }

  private void addFilesToSearchForGivenWord(String refname, boolean endTransaction, GlobalCompilingVisitor.OccurenceKind kind) {
    if (!getContext().getSearchHelper().doOptimizing()) {
      return;
    }
    if (ourReservedWords.contains(refname)) return; // skip our special annotations !!!

    boolean addedSomething = false;

    if (kind == GlobalCompilingVisitor.OccurenceKind.CODE) {
      addedSomething = getContext().getSearchHelper().addWordToSearchInCode(refname);
    }
    else if (kind == GlobalCompilingVisitor.OccurenceKind.COMMENT) {
      addedSomething = getContext().getSearchHelper().addWordToSearchInComments(refname);
    }
    else if (kind == GlobalCompilingVisitor.OccurenceKind.LITERAL) {
      addedSomething = getContext().getSearchHelper().addWordToSearchInLiterals(refname);
    }

    if (addedSomething && endTransaction) {
      getContext().getSearchHelper().endTransaction();
    }
  }

  public void handleReferenceText(String refname) {
    if (refname == null) return;

    if (getContext().getPattern().isTypedVar(refname)) {
      SubstitutionHandler handler = (SubstitutionHandler)getContext().getPattern().getHandler(refname);
      RegExpPredicate predicate = MatchingHandler.getSimpleRegExpPredicate(handler);
      if (IsNotSuitablePredicate(predicate, handler)) {
        return;
      }

      refname = predicate.getRegExp();

      if (handler.isStrictSubtype() || handler.isSubtype()) {
        if (getContext().getSearchHelper().addDescendantsOf(refname, handler.isSubtype())) {
          getContext().getSearchHelper().endTransaction();
        }

        return;
      }
    }

    addFilesToSearchForGivenWord(refname, true, GlobalCompilingVisitor.OccurenceKind.CODE);
  }

  public void processTokenizedName(String name, boolean skipComments, GlobalCompilingVisitor.OccurenceKind kind) {
    WordTokenizer tokenizer = new WordTokenizer(name);
    for (Iterator<String> i = tokenizer.iterator(); i.hasNext();) {
      String nextToken = i.next();
      if (skipComments &&
          (nextToken.equals("/*") || nextToken.equals("/**") || nextToken.equals("*/") || nextToken.equals("*") || nextToken.equals("//"))
        ) {
        continue;
      }

      Matcher matcher = ourAlternativePattern.matcher(nextToken);
      if (matcher.matches()) {
        StringTokenizer alternatives = new StringTokenizer(matcher.group(1), "|");
        while (alternatives.hasMoreTokens()) {
          addFilesToSearchForGivenWord(alternatives.nextToken(), !alternatives.hasMoreTokens(), kind);
        }
      }
      else {
        addFilesToSearchForGivenWord(nextToken, true, kind);
      }
    }
  }

  public static enum OccurenceKind {
    LITERAL, COMMENT, CODE
  }

  private static class WordTokenizer {
    private final List<String> myWords = new LinkedList<String>();

    WordTokenizer(String text) {
      final StringTokenizer tokenizer = new StringTokenizer(text);
      Matcher matcher = null;

      while (tokenizer.hasMoreTokens()) {
        String nextToken = tokenizer.nextToken();
        if (matcher == null) {
          matcher = ourWordSearchPattern.matcher(nextToken);
        }
        else {
          matcher.reset(nextToken);
        }

        nextToken = (matcher.matches()) ? matcher.group(1) : nextToken;
        int lastWordStart = 0;
        int i;
        for (i = 0; i < nextToken.length(); ++i) {
          if (!Character.isJavaIdentifierStart(nextToken.charAt(i))) {
            if (i != lastWordStart) {
              myWords.add(nextToken.substring(lastWordStart, i));
            }
            lastWordStart = i + 1;
          }
        }

        if (i != lastWordStart) {
          myWords.add(nextToken.substring(lastWordStart, i));
        }
      }
    }

    Iterator<String> iterator() {
      return myWords.iterator();
    }
  }
}
