package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.patterns.ElementPattern;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.Matcher;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Python keyword completion contributor.
 * <br/>
 * <b>NOTE: many matchers here use original tree, not the grafted identifier in the copied tree.</b>
 * This should not lead to any problems, because all such code is private, and the user data used to pass the original
 * element is cleared after matching.
 * User: dcheryasov
 * Date: Sep 8, 2008
 */
@SuppressWarnings({"InstanceVariableOfConcreteClass"})
public class PyKeywordCompletionContributor extends CompletionContributor {

  private static Key<PsiElement> ORG_ELT = Key.create("PyKeywordCompletionContributor original element");
  private static Key<Integer> ORG_OFFSET = Key.create("PyKeywordCompletionContributor original offset");

  /* NOTE: a better matching language would capture the matched elements, so that constraiant on them are easy to add, like:
   * condition = inside(PyStatement.class).withPrevSibling(psiElement(PyConditionalStatement.class).withChild(PyElsePart.class))
   *             ^ we                      ^ the PyStatement which just matched                     ^ the PyConditionalStatement's 
  */
  private static class PrecededByFilter implements ElementFilter {
    ElementPattern<? extends PsiElement>[] myConstraints;

    /**
     * Used to look for a statment right above us to which we might want to add a part.
     * Matches iff our element or any its parent is preceded by a sibling element that satisfies all given constraints.
     * Parents above PyStatementList and PsiFile levels are not considered, because no syntactic construction
     * spans multiple non-nested statements.
     * @param constraints which the preceding element must satisfy
     */
    public PrecededByFilter(ElementPattern<? extends PsiElement>... constraints) {
      myConstraints = constraints;
    }

    public boolean isAcceptable(Object what, PsiElement context) {
      if (!(what instanceof UserDataHolder)) return false; // can't dream to match
      PsiElement element = ((UserDataHolder)what).getUserData(ORG_ELT);
      if (element == null) return false; // we're not from here
      ProcessingContext ctx = new ProcessingContext();
      // climb until "after what" matches
      while (element != null && !(element instanceof PsiFile) && !(element instanceof PyStatementList)) { // these have no worthy prev siblings
        PsiElement preceding = element.getPrevSibling();
        // TODO: make 'skip whitespece' configurable
        while (preceding instanceof PsiWhiteSpace) preceding = preceding.getPrevSibling();
        if (preceding != null) {
          boolean matched = true;
          for (ElementPattern<? extends PsiElement> constraint : myConstraints) {
            if (!constraint.accepts(preceding, ctx)) {
              matched = false;
              break;
            }
          }
          if (matched) return true;
        }
        element = element.getParent(); // bad luck, climb
      }
      // all above failed
      return false;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }


  private abstract static class MatcherBasedFilter implements ElementFilter {

    abstract Matcher getMatcher();

    public boolean isAcceptable(Object element, PsiElement context) {
      return ((element instanceof PsiElement) && getMatcher().search((PsiElement)element) != null);
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }

  /**
   * Matches if element is somewhere inside a loop, but not within a class or function inside that loop.
   */
  private static class InLoopFilter extends MatcherBasedFilter {
    Matcher getMatcher() { return SyntaxMatchers.LOOP_CONTROL; }
  }


  /**
   * Matches if element is somewhere inside a loop, but not within a class or function inside that loop.
   */
  private static class InFunctionBodyFilter extends MatcherBasedFilter {
    Matcher getMatcher() { return SyntaxMatchers.IN_FUNCTION; }
  }

  private static class InDefinitionFilter extends MatcherBasedFilter {
    Matcher getMatcher() { return SyntaxMatchers.IN_DEFINITION; }
  }

  private static class InFinallyNoLoopFilter extends MatcherBasedFilter {
    Matcher getMatcher() { return SyntaxMatchers.IN_FINALLY_NO_LOOP; }
  }

  /**
   * Ported from TreeElementPattern.insideSequence for the sake of extraction of non-fake ctrl+space element.
   */
  private static class InSequenceFilter implements ElementFilter {
    ElementPattern<? extends PsiElement>[] myPatterns;

    /**
     * Matches is a given sequence of patterns match each on certain parents of the element, in the order given. The match may start well
     * above the element, and the matching elemements may come with gaps. But once the first pattern has matched, it will not
     * be reconsidered if the rest did not match.
     * The search will not continue above PsiFile level.
     * @param patterns to match; first pattern is for the deepest element, last is for the outermost.
     */
    public InSequenceFilter(@NotNull final ElementPattern<? extends PsiElement>... patterns) {
      myPatterns = patterns;
    }

    public boolean isAcceptable(Object what, PsiElement context) {
      if (!(what instanceof UserDataHolder)) return false; // can't dream to match
      if (myPatterns.length <= 0) return false; // sanity check
      int patIndex = 0;
      PsiElement true_elt = ((UserDataHolder)what).getUserData(ORG_ELT);
      if (true_elt == null) return false; // we're not from here
      PsiElement element = true_elt;
      ProcessingContext ctx = new ProcessingContext();
      // climb until first condition matches
      while (element != null && !myPatterns[patIndex].getCondition().accepts(element, ctx)) {
        element = element.getParent();
      }
      if (element == null) return false;
      if (patIndex == myPatterns.length-1) return true; // the degenerate case of single pattern
      // make sure the rest matches, too
      do {
        element = element.getParent();
        patIndex += 1;
        if (element == null || element instanceof PsiDirectory) return false; // through the roof
        if (!myPatterns[patIndex].getCondition().accepts(element, ctx)) return false; // an unmatched gap
      } while (patIndex+1 < myPatterns.length);
      return true; // patterns finished without a fail
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }


  /**
   * Matches places where a keyword-based statement might be appropriate.
   */
  private static class StatementFitFilter implements ElementFilter {

    public StatementFitFilter() { }


    public boolean isAcceptable(Object element, PsiElement context) {
      if (element instanceof PsiElement) {
        final ASTNode ctxNode = context.getNode();
        if (ctxNode != null && ctxNode.getElementType() == PyTokenTypes.STRING_LITERAL) return false; // no sense inside string
        PsiElement p = (PsiElement)element;
        //int org_offset = p.getUserData(ORG_OFFSET); // saved by fillCompletionVariants()
        p = p.getUserData(ORG_ELT); // saved by fillCompletionVariants().
        if (p == null) return false; // just in case
        int first_offset = p.getTextOffset();
        // we must be a stmt ourselves, not a part of another stmt
        // try to climb to the stmt level with the same offset
        while (true) {
          if (p.getTextOffset() != first_offset) return false;
          if (p instanceof PyStatement) break;
          else if (p == null) return false;
          p = p.getParent();
        }
        // so, a stmt begins with us
        // isn't there an incorrect stmt before us on the same line?
        PsiElement container = p.getParent();
        if (container instanceof PyStatementList || container instanceof PsiFile) {
          PsiElement prev = p.getPrevSibling();
          while (prev instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
          if (prev == null) return true; // there was only whitespace before us
          if (prev instanceof PyStatement) { // a non-stmt would be something strange
            if (prev.getLastChild() instanceof PsiErrorElement) {
              // prev stmt ends with an error. are we on the same line?
              PsiDocumentManager docMgr = PsiDocumentManager.getInstance(p.getProject());
              Document doc = docMgr.getDocument(p.getContainingFile());
              if (doc != null) {
                if (doc.getLineNumber(prev.getTextRange().getEndOffset()) == doc.getLineNumber(first_offset)) {
                  return false; // same line
                }
              }
            }
            return true; // we follow a well-formed stmt
          }
        }
      }
      return false;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true; // can't tell outright
    }
  }

  /* Note: conserved just in case.
   * Matches if before our element, skipping whitespace but on the same line, comes given string.
   * The string not necessarily begins at the start of a PSI element (and may span several).
   * Note that matching against newlines is possible
   */
  /*
  private static class RightAfterFilter implements ElementFilter {
    String myText;

    public RightAfterFilter(String text) {
      myText = text;
    }

    public boolean isAcceptable(Object what, PsiElement context) {
      if (!(what instanceof PsiElement)) return false;
      PsiElement p = (PsiElement)what;
      p = p.getUserData(ORG_ELT); // saved by fillCompletionVariants().
      if (p == null) return false; // just in case
      PsiElement feeler = p;
      while (true) { // skip all whitespace to the left but the last
        PsiElement seeker = feeler.getPrevSibling();
        if (!(seeker instanceof PsiWhiteSpace) || seeker.getText().indexOf('\n') < 0) break;
        else feeler = seeker;
      }
      int endpoint = feeler.getTextOffset()-1;
      PsiDocumentManager docMgr = PsiDocumentManager.getInstance(p.getProject());
      Document doc = docMgr.getDocument(p.getContainingFile());
      if (doc != null) {
        if (myText.equals(doc.getCharsSequence().subSequence(endpoint - myText.length(), endpoint).toString())) return true;
      }
      return false;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }
  */

  /**
   * Matches if an element has nothing but whitespace to the left of it, up to a newline or BOF.
   * NOTE: if lexer detected indents and/or EOLs as separete entities, this filter would not be needed, or would trivial work with PSI.
   */
  private static class StartOfLineFilter implements ElementFilter {
    public boolean isAcceptable(Object what, PsiElement context) {
      if (!(what instanceof PsiElement)) return false;
      PsiElement p = (PsiElement)what;
      p = p.getUserData(ORG_ELT); // saved by fillCompletionVariants().
      if (p == null || p instanceof PsiComment) return false; // just in case
      int point = p.getTextOffset();
      PsiDocumentManager docMgr = PsiDocumentManager.getInstance(p.getProject());
      Document doc = docMgr.getDocument(p.getContainingFile());
      if (doc != null) {
        CharSequence chs = doc.getCharsSequence();
        char c;
        do { // scan to the left for a EOL
          point -= 1;
          if (point < 0) return true; // we're at BOF
          c = chs.charAt(point);
          if (c == '\n') return true;
        } while (c == ' ' || c == '\t');
      }
      return false;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }

  // ====== conditions

  private static final PsiElementPattern.Capture<PsiElement> IN_COMMENT =
    psiElement().inside(PsiComment.class)
  ;

  private static final PsiElementPattern.Capture<PsiElement> AFTER_QUALIFIER =
    psiElement().afterLeaf(".")
  ;

  private static final FilterPattern FIRST_ON_LINE = new FilterPattern(new StartOfLineFilter());

  private static final PsiElementPattern.Capture<PsiElement> IN_IMPORT_AFTER_REF =
    psiElement().afterLeaf(psiElement().withElementType(PyTokenTypes.IDENTIFIER).inside(PyReferenceExpression.class).inside(PyImportElement.class))
  ;

  private static final PsiElementPattern.Capture<PsiElement> IN_FROM_IMPORT_AFTER_REF =
    psiElement().afterLeaf(
      psiElement().withElementType(PyTokenTypes.IDENTIFIER).inside(PyReferenceExpression.class).inside(PyFromImportStatement.class)
    )
  ;

  private static final PsiElementPattern.Capture<PsiElement> IN_WITH_AFTER_REF =
    psiElement().afterLeaf(psiElement()
      .withElementType(PyTokenTypes.IDENTIFIER)
      .inside(PyReferenceExpression.class)
      .inside(PyWithStatement.class)
    )
  ;

  private static final FilterPattern IN_COND_STMT = new FilterPattern(
    new InSequenceFilter(psiElement(PyStatementList.class), psiElement(PyConditionalStatementPart.class))
  );


  private static final FilterPattern IN_IF_BODY = new FilterPattern(
    new InSequenceFilter(psiElement(PyStatementList.class), psiElement(PyIfPart.class))
  );

  private static final FilterPattern IN_LOOP = new FilterPattern(new InLoopFilter());

  // not eaxctly a beauty
  private static final PsiElementPattern.Capture<PsiElement> BEFORE_COND =
    psiElement()
      .inside(PyConditionalStatementPart.class)
      .andOr(
        psiElement().afterLeaf(psiElement().withText("if")),
        psiElement().afterLeaf(psiElement().withText("elif")),
        psiElement().afterLeaf(psiElement().withText("while"))
      )
  ;

  private static final PsiElementPattern.Capture<PsiElement> IN_IMPORT_STMT =
    psiElement().inside(
      StandardPatterns.or(
        psiElement(PyImportStatement.class), psiElement(PyFromImportStatement.class)
      )
    )
  ;

  private static final PsiElementPattern.Capture<PsiElement> IN_PARAM_LIST = psiElement().inside(PyParameterList.class);
  private static final PsiElementPattern.Capture<PsiElement> IN_ARG_LIST = psiElement().inside(PyArgumentList.class);


  private static final FilterPattern IN_DEF_BODY = new FilterPattern(new InFunctionBodyFilter());

  private static final FilterPattern IN_TRY_BODY = new FilterPattern(
    new InSequenceFilter(psiElement(PyStatementList.class), psiElement(PyTryPart.class))
  );

  private static final FilterPattern IN_EXCEPT_BODY = new FilterPattern(
    new InSequenceFilter(psiElement(PyStatementList.class), psiElement(PyExceptPart.class))
  );

  private static final FilterPattern IN_DEFINITION = new FilterPattern(new InDefinitionFilter());

  private static final FilterPattern AFTER_IF = new FilterPattern(new PrecededByFilter(psiElement(PyIfStatement.class)));
  private static final FilterPattern AFTER_TRY = new FilterPattern(new PrecededByFilter(psiElement(PyTryExceptStatement.class)));

  private static final FilterPattern AFTER_LOOP_NO_ELSE = new FilterPattern(new PrecededByFilter(
    psiElement()
      .withChild(StandardPatterns.or(psiElement(PyWhileStatement.class), psiElement(PyForStatement.class)))
      .withLastChild(StandardPatterns.not(psiElement(PyElsePart.class)))
  ));

  private static final FilterPattern AFTER_COND_STMT_NO_ELSE = new FilterPattern(new PrecededByFilter(
    psiElement().withChild(psiElement(PyConditionalStatementPart.class)).withLastChild(StandardPatterns.not(psiElement(PyElsePart.class)))
  ));

  private static final FilterPattern AFTER_TRY_NO_ELSE = new FilterPattern(new PrecededByFilter(
    psiElement().withChild(psiElement(PyTryPart.class)).withLastChild(StandardPatterns.not(psiElement(PyElsePart.class)))
  ));

  private static final FilterPattern IN_FINALLY_NO_LOOP = new FilterPattern(new InFinallyNoLoopFilter());

  private static final FilterPattern IN_BEGIN_STMT = new FilterPattern(new StatementFitFilter());

  private static final FilterPattern INSIDE_EXPR = new FilterPattern(new PrecededByFilter(
    psiElement(PyExpression.class)
  ));

  private static final FilterPattern INSIDE_EXPR_AFTER_IF = new FilterPattern(new PrecededByFilter(
    psiElement(PyExpression.class).afterLeaf("if")
  ));


  /**
   * Tail type that adds a space and a colon and puts cursor before colon. Used in things like "if".
   */
  private static TailType PRE_COLON = new TailType() {
    public int processTail(Editor editor, int tailOffset) {
      tailOffset = insertChar(editor, insertChar(editor, tailOffset, ' '), ':');
      return moveCaret(editor, tailOffset, -1); // stand before ":"
    }
  };

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    final PsiElement original = parameters.getPosition();
    try {
      original.putUserData(ORG_ELT, parameters.getOriginalPosition());
      original.putUserData(ORG_OFFSET, parameters.getOffset());
      // we'll be safe accessing original file in patterns, because pattern checks run in a ReadAction.
      super.fillCompletionVariants(parameters, result);
    }
    finally {
      // help gc a bit
      original.putUserData(ORG_ELT, null);
      original.putUserData(ORG_OFFSET, null);
    }
  }

  // ======

  private static void putKeywords(@NonNls @NotNull String[] words, TailType tail, final CompletionResultSet result) {
    for (String s : words) {
      result.addElement(TailTypeDecorator.createDecorator(LookupElementBuilder.create(s).setBold(), tail));
    }
  }

  private void addPreColonStatements() {
    extend(
      CompletionType.BASIC,
      psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(FIRST_ON_LINE)
        //.andNot(RIGHT_AFTER_COLON)
        .andNot(IN_IMPORT_STMT)
        .andNot(IN_PARAM_LIST)
        .andNot(IN_ARG_LIST)
        .andNot(IN_DEFINITION)
        .andNot(BEFORE_COND)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] pre_strings = {"def", "class", "for", "if", "while", "with"};
          final @NonNls String[] colon_strings = {"try"};
          putKeywords(pre_strings, PRE_COLON, result);
          putKeywords(colon_strings, TailType.CASE_COLON, result);
        }
      }
    );
  }
  
  private void addStatements() {
    extend(
      CompletionType.BASIC,
      psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(IN_BEGIN_STMT)
        .andNot(IN_IMPORT_STMT)
        .andNot(IN_PARAM_LIST)
        .andNot(IN_ARG_LIST)
        .andNot(IN_DEFINITION)
        .andNot(BEFORE_COND)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"assert", "del", "exec", "from", "import", "lambda", "print", "raise"};
          final @NonNls String[] just_strings = {"pass"};
          putKeywords(space_strings, TailType.SPACE, result);
          putKeywords(just_strings, TailType.NONE, result);
        }
      }
    );
  }

  private void addBreak() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(IN_BEGIN_STMT)
        .andNot(AFTER_QUALIFIER)
        .andNot(IN_PARAM_LIST)
        .andNot(IN_ARG_LIST)
        .andOr(IN_LOOP, AFTER_LOOP_NO_ELSE)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] strings = {"break"};
          putKeywords(strings, TailType.NONE, result);
        }
      }
    );
  }

  private void addContinue() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(IN_BEGIN_STMT)
        .andNot(AFTER_QUALIFIER)
        .andNot(IN_PARAM_LIST)
        .andNot(IN_ARG_LIST)
        .andNot(IN_FINALLY_NO_LOOP)
        .andOr(IN_LOOP, AFTER_LOOP_NO_ELSE)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] strings = {"continue"};
          putKeywords(strings, TailType.NONE, result);
        }
      }
    );
  }

  private void addWithinFuncs() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(IN_DEF_BODY)
        .and(IN_BEGIN_STMT)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"global", "return", "yield"};
          putKeywords(space_strings, TailType.SPACE, result);
        }
      }
    );
  }

  private void addWithinIf() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(FIRST_ON_LINE)
        .andOr(IN_IF_BODY, AFTER_IF)  // NOTE: does allow 'elif' after 'else', may be useful for easier reordering of branches
        //.andNot(RIGHT_AFTER_COLON)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] strings = {"elif"};
          putKeywords(strings, PRE_COLON, result);
          // TODO: have it dedent properly
        }
      }
    );
  }

  private void addWithinTry() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(FIRST_ON_LINE)
        .andOr(IN_TRY_BODY, AFTER_TRY)
        //.andNot(RIGHT_AFTER_COLON)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] pre_colon_strings = {"except"};
          final @NonNls String[] colon_strings = {"finally"};
          putKeywords(pre_colon_strings, PRE_COLON, result);
          putKeywords(colon_strings, TailType.CASE_COLON, result);
          // TODO: have it dedent properly
        }
      }
    );
  }

  private void addElse() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(FIRST_ON_LINE)
        .andOr(IN_COND_STMT, IN_TRY_BODY, IN_EXCEPT_BODY, AFTER_COND_STMT_NO_ELSE, AFTER_TRY_NO_ELSE)
        //.andNot(RIGHT_AFTER_COLON)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] colon_strings = {"else"};
          putKeywords(colon_strings, TailType.CASE_COLON, result); // TODO: have it dedent properly
        }
      }
    );
  }

  private void addInfixOperators() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .andNot(IN_COMMENT)
        .andNot(BEFORE_COND)
        .andNot(IN_IMPORT_STMT) // expressions there are not logical anyway
        .andNot(IN_PARAM_LIST)
        .andNot(IN_DEFINITION)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"and", "or", "is", "in"};
          putKeywords(space_strings, TailType.SPACE, result);
        }
      }
    );
  }

  private void addNot() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .andNot(IN_COMMENT)
        .andNot(IN_IMPORT_STMT)
        .andNot(IN_PARAM_LIST)
        .andNot(IN_DEFINITION)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"not"};
          putKeywords(space_strings, TailType.SPACE, result);
        }
      }
    );
  }

  private void addAs() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .andOr(IN_IMPORT_AFTER_REF, IN_WITH_AFTER_REF) 
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"as"};
          putKeywords(space_strings, TailType.SPACE, result);
        }
      }
    );
  }

  private void addImportInFrom() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(IN_FROM_IMPORT_AFTER_REF)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"import"};
          putKeywords(space_strings, TailType.SPACE, result);
        }
      }
    );
  }

  // FIXME: conditions must be severely reworked
  private void addExprIf() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(INSIDE_EXPR)
        .andNot(IN_IMPORT_STMT) // expressions there are not logical anyway
        //.andNot(IN_PARAM_LIST)
        .andNot(IN_DEFINITION)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"if"};
          putKeywords(space_strings, TailType.SPACE, result);
        }
      }
    );
  }

  // FIXME: conditions must be severely reworked
  private void addExprElse() {
    extend(
      CompletionType.BASIC, psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(INSIDE_EXPR_AFTER_IF)
        .andNot(IN_IMPORT_STMT) // expressions there are not logical anyway
        //.andNot(IN_PARAM_LIST)
        .andNot(IN_DEFINITION)
        .andNot(AFTER_QUALIFIER)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"else"};
          putKeywords(space_strings, TailType.SPACE, result);
        }
      }
    );
  }

  public PyKeywordCompletionContributor() {
    addStatements();
    addPreColonStatements();
    addWithinIf();
    addElse();
    addBreak();
    addContinue();
    addWithinFuncs();
    addWithinTry();
    addInfixOperators();
    addNot();
    addAs();
    addImportInFrom();
    //addExprIf();
    //addExprElse();
  }
}
