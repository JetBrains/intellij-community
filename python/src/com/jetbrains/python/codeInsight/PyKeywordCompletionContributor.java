package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.Matcher;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

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
public class PyKeywordCompletionContributor extends PySeeingOriginalCompletionContributor {

  private static boolean isEmptyOriginalFile(PsiElement context) {
    final PsiFile originalFile = context.getContainingFile().getOriginalFile();
    if (originalFile.getTextLength() == 0) {
      // completion in empty file (PY-1845)
      return true;
    }
    return false;
  }


  /**
   * Matches if element is somewhere inside a loop, but not within a class or function inside that loop.
   */
  private static class InLoopFilter extends MatcherBasedFilter {
    Matcher getMatcher() {
      return SyntaxMatchers.LOOP_CONTROL;
    }
  }


  /**
   * Matches if element is somewhere inside a loop, but not within a class or function inside that loop.
   */
  private static class InFunctionBodyFilter extends MatcherBasedFilter {
    Matcher getMatcher() {
      return SyntaxMatchers.IN_FUNCTION;
    }
  }

  private static class InDefinitionFilter extends MatcherBasedFilter {
    Matcher getMatcher() {
      return SyntaxMatchers.IN_DEFINITION;
    }
  }

  private static class InFinallyNoLoopFilter extends MatcherBasedFilter {
    Matcher getMatcher() {
      return SyntaxMatchers.IN_FINALLY_NO_LOOP;
    }
  }

  /**
   * Matches places where a keyword-based statement might be appropriate.
   */
  private static class StatementFitFilter implements ElementFilter {

    public StatementFitFilter() {
    }


    public boolean isAcceptable(Object element, PsiElement context) {
      if (element instanceof PsiElement) {
        final ASTNode ctxNode = context.getNode();
        if (ctxNode != null && ctxNode.getElementType() == PyTokenTypes.STRING_LITERAL) return false; // no sense inside string
        PsiElement p = (PsiElement)element;
        //int org_offset = p.getUserData(ORG_OFFSET); // saved by fillCompletionVariants()
        p = p.getUserData(ORG_ELT); // saved by fillCompletionVariants().
        if (p == null) {
          return isEmptyOriginalFile(context);
        }
        int first_offset = p.getTextOffset();
        // we must be a stmt ourselves, not a part of another stmt
        // try to climb to the stmt level with the same offset
        while (true) {
          if (p == null) return false;
          if (p.getTextOffset() != first_offset) return false;
          if (p instanceof PyStatement) break;
          p = p.getParent();
        }
        // so, a stmt begins with us
        // isn't there an incorrect stmt before us on the same line?
        PsiElement container = p.getParent();
        if (container instanceof PyStatementList || container instanceof PsiFile) {
          PsiElement prev = p.getPrevSibling();
          while (prev instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
          if (prev == null) return true; // there was only whitespace before us
          if (prev instanceof PyStatement || prev instanceof PsiComment) { // a non-stmt would be something strange
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
   * NOTE: if lexer detected indents and/or EOLs as separate entities, this filter would not be needed, or would trivially work with PSI.
   */
  private static class StartOfLineFilter implements ElementFilter {
    public boolean isAcceptable(Object what, PsiElement context) {
      if (!(what instanceof PsiElement)) return false;
      PsiElement p = (PsiElement)what;
      p = p.getUserData(ORG_ELT); // saved by fillCompletionVariants().
      if (p == null) {
        return isEmptyOriginalFile(context);
      }
      else if (p instanceof PsiComment) return false; // just in case
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
        }
        while (c == ' ' || c == '\t');
      }
      return false;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }

  private static class Py3kFilter implements ElementFilter {
    public boolean isAcceptable(Object element, PsiElement context) {
      if (!(element instanceof PsiElement)) {
        return false;
      }
      final PsiFile containingFile = ((PsiElement)element).getContainingFile();
      return containingFile instanceof PyFile && ((PyFile)containingFile).getLanguageLevel().isPy3K();
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }

  private static class NotParameterOrDefaultValue implements ElementFilter {

    @Override
    public boolean isAcceptable(Object element, PsiElement context) {
      if (!(element instanceof PsiElement)) {
        return false;
      }
      PsiElement psiElement = (PsiElement) element;
      PsiElement definition = PsiTreeUtil.getParentOfType(psiElement, PyDocStringOwner.class, false, PyStatementList.class);
      if (definition != null) {
        if (PsiTreeUtil.getParentOfType(psiElement, PyParameterList.class) == null) {
          return true;
        }
        PyParameter param = PsiTreeUtil.getParentOfType(psiElement, PyParameter.class);
        if (param != null) {
          PyExpression defaultValue = param.getDefaultValue();
          if (defaultValue != null && PsiTreeUtil.isAncestor(defaultValue, psiElement, false)) {
            return true;
          }
        }
        return false;
      }
      return true;
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }

  private static ElementPattern NOT_PARAMETER_OR_DEFAULT_VALUE = new FilterPattern(new NotParameterOrDefaultValue());

  // ====== conditions

  private static final PsiElementPattern.Capture<PsiElement> IN_COMMENT =
    psiElement().inside(PsiComment.class);

  private static final PsiElementPattern.Capture<PsiElement> IN_STRING_LITERAL =
    psiElement().inside(PyStringLiteralExpression.class);

  public static final PsiElementPattern.Capture<PsiElement> AFTER_QUALIFIER =
    psiElement().afterLeaf(psiElement().withText(".").inside(PyReferenceExpression.class));

  private static final FilterPattern FIRST_ON_LINE = new FilterPattern(new StartOfLineFilter());

  private static final PsiElementPattern.Capture<PsiElement> IN_IMPORT_AFTER_REF =
    psiElement()
      .afterLeaf(psiElement().withElementType(PyTokenTypes.IDENTIFIER).inside(PyReferenceExpression.class).inside(PyImportElement.class));

  public static final PsiElementPattern.Capture<PsiElement> IN_FROM_IMPORT_AFTER_REF =
    psiElement().afterLeaf(
      or(psiElement().withElementType(PyTokenTypes.IDENTIFIER).inside(PyReferenceExpression.class),
         psiElement().withElementType(PyTokenTypes.DOT))
      ).inside(PyFromImportStatement.class);

  private static final PsiElementPattern.Capture<PsiElement> IN_WITH_AFTER_REF =
    psiElement().afterLeaf(psiElement()
      .withElementType(PyTokenTypes.IDENTIFIER)
      .inside(PyReferenceExpression.class)
      .inside(PyWithStatement.class)
    );

  private static final FilterPattern IN_COND_STMT = new FilterPattern(
    new InSequenceFilter(psiElement(PyStatementList.class), psiElement(PyConditionalStatementPart.class))
  );


  private static final FilterPattern IN_IF_BODY = new FilterPattern(
    new InSequenceFilter(psiElement(PyStatementList.class), psiElement(PyIfPart.class))
  );

  private static final FilterPattern IN_LOOP = new FilterPattern(new InLoopFilter());

  // not exactly a beauty
  private static final PsiElementPattern.Capture<PsiElement> BEFORE_COND =
    psiElement()
      .inside(PyConditionalStatementPart.class)
      .andOr(
        psiElement().afterLeaf(psiElement().withText("if")),
        psiElement().afterLeaf(psiElement().withText("elif")),
        psiElement().afterLeaf(psiElement().withText("while"))
      );

  private static final PsiElementPattern.Capture<PsiElement> IN_IMPORT_STMT =
    psiElement().inside(
      StandardPatterns.or(
        psiElement(PyImportStatement.class), psiElement(PyFromImportStatement.class)
      )
    );

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

  private static final FilterPattern PY3K = new FilterPattern(new Py3kFilter());


  /**
   * Tail type that adds a space and a colon and puts cursor before colon. Used in things like "if".
   */
  private static final TailType PRE_COLON = new TailType() {
    public int processTail(Editor editor, int tailOffset) {
      tailOffset = insertChar(editor, insertChar(editor, tailOffset, ' '), ':');
      return moveCaret(editor, tailOffset, -1); // stand before ":"
    }
  };

  // ======

  private static void putKeywords(@NonNls @NotNull String[] words, TailType tail, final CompletionResultSet result) {
    for (String s : words) {
      result.addElement(TailTypeDecorator.withTail(new PythonLookupElement(s, true, null), tail));
    }
  }

  private static void putKeyword(
    @NotNull @NonNls String keyword,
    InsertHandler<PythonLookupElement> handler,
    TailType tail,
    CompletionResultSet result) {
    final PythonLookupElement lookup_elt = new PythonLookupElement(keyword, true, null);
    lookup_elt.setHandler(handler);
    result.addElement(TailTypeDecorator.withTail(lookup_elt, tail));
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
        .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
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
    PsiElementPattern.Capture<PsiElement> inStatement = psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .and(IN_BEGIN_STMT)
      .andNot(IN_IMPORT_STMT)
      .andNot(IN_PARAM_LIST)
      .andNot(IN_ARG_LIST)
      .andNot(IN_DEFINITION)
      .andNot(BEFORE_COND)
      .andNot(AFTER_QUALIFIER);

    extend(
      CompletionType.BASIC,
      inStatement,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"assert", "del", "exec", "from", "import", "raise"};
          final @NonNls String[] just_strings = {"pass"};
          putKeywords(space_strings, TailType.SPACE, result);
          putKeywords(just_strings, TailType.NONE, result);
        }
      }
    );

    extend(CompletionType.BASIC, inStatement.andNot(PY3K),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               putKeywords(new String[]{"print"}, TailType.SPACE, result);
             }
           });
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
      new PyKeywordCompletionProvider(TailType.NONE, "break")
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
      new PyKeywordCompletionProvider(TailType.NONE, "continue")
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
      new PyKeywordCompletionProvider("global", "return", "yield")
    );

    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .and(IN_DEF_BODY)
      .and(IN_BEGIN_STMT)
      .and(PY3K)
      .andNot(AFTER_QUALIFIER)
      ,
      new PyKeywordCompletionProvider("nonlocal")
    );
  }

  private void addWithinIf() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .and(FIRST_ON_LINE)
      .andOr(IN_IF_BODY, AFTER_IF)  // NOTE: does allow 'elif' after 'else', may be useful for easier reordering of branches
        //.andNot(RIGHT_AFTER_COLON)
      .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          putKeyword("elif", UnindentingInsertHandler.INSTANCE, PRE_COLON, result);
        }
      }
    );
  }

  private void addWithinTry() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .and(FIRST_ON_LINE)
      .andOr(IN_TRY_BODY, IN_EXCEPT_BODY, AFTER_TRY)
        //.andNot(RIGHT_AFTER_COLON)
      .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          putKeyword("except", UnindentingInsertHandler.INSTANCE, PRE_COLON, result);
          putKeyword("finally", UnindentingInsertHandler.INSTANCE, TailType.CASE_COLON, result);
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
      .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          putKeyword("else", UnindentingInsertHandler.INSTANCE, TailType.CASE_COLON, result);
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
      .andNot(AFTER_QUALIFIER).
        andNot(IN_STRING_LITERAL).and(IN_BEGIN_STMT)
      ,
      new PyKeywordCompletionProvider("and", "or", "is", "in")
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
      .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
      ,
      new PyKeywordCompletionProvider("not", "lambda")
    );
  }

  private void addPy3kLiterals() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .and(PY3K)
      .andNot(IN_COMMENT)
      .andNot(IN_IMPORT_STMT)
      .and(NOT_PARAMETER_OR_DEFAULT_VALUE)
      .andNot(AFTER_QUALIFIER)
      ,
      new PyKeywordCompletionProvider(TailType.NONE, "True", "False", "None")
    );
  }

  private void addAs() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .andOr(IN_IMPORT_AFTER_REF, IN_WITH_AFTER_REF)
      .andNot(AFTER_QUALIFIER)
      ,
      new PyKeywordCompletionProvider("as")
    );
  }

  private void addImportInFrom() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .and(IN_FROM_IMPORT_AFTER_REF)
      .andNot(AFTER_QUALIFIER)
      ,
      new PyKeywordCompletionProvider("import")
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
      .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(@NotNull final CompletionParameters parameters,
                                      final ProcessingContext context,
                                      @NotNull final CompletionResultSet result
        ) {
          final @NonNls String[] space_strings = {"if"};
          putKeywords(space_strings, TailType.SPACE, result);
        }
      }
    );
  }

  private void addExprElse() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .afterLeafSkipping(psiElement().whitespace(),
                         psiElement().inside(psiElement(PyConditionalExpression.class))
                           .and(psiElement().afterLeaf("if")))
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(@NotNull final CompletionParameters parameters,
                                      final ProcessingContext context,
                                      @NotNull final CompletionResultSet result
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
    addPy3kLiterals();
    //addExprIf();
    addExprElse();
  }

  private static class PyKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final String[] myKeywords;
    private final TailType myTailType;

    private PyKeywordCompletionProvider(String... keywords) {
      this(TailType.SPACE, keywords);
    }

    private PyKeywordCompletionProvider(TailType tailType, String... keywords) {
      myKeywords = keywords;
      myTailType = tailType;
    }

    protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context,
                                  @NotNull final CompletionResultSet result) {
      putKeywords(myKeywords, myTailType, result);
    }
  }
}
