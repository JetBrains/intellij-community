/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.UnindentingInsertHandler;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
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
public class PyKeywordCompletionContributor extends CompletionContributor {
  /**
   * Matches places where a keyword-based statement might be appropriate.
   */
  private static class StatementFitFilter implements ElementFilter {

    public StatementFitFilter() {
    }


    public boolean isAcceptable(Object element, PsiElement context) {
      if (element instanceof PsiElement) {
        final ASTNode ctxNode = context.getNode();
        if (ctxNode != null && PyTokenTypes.STRING_NODES.contains(ctxNode.getElementType())) return false; // no sense inside string
        PsiElement p = (PsiElement)element;
        int firstOffset = p.getTextRange().getStartOffset();
        // we must be a stmt ourselves, not a part of another stmt
        // try to climb to the stmt level with the same offset
        while (true) {
          if (p == null) return false;
          if (p.getTextRange().getStartOffset() != firstOffset) return false;
          if (p instanceof PyStatement) break;
          p = p.getParent();
        }
        // so, a stmt begins with us
        // isn't there an incorrect stmt before us on the same line?
        PsiElement container = p.getParent();
        if (!(container instanceof PyElement)) return true;
        if (container instanceof PyStatementList || container instanceof PsiFile) {
          PsiElement prev = p.getPrevSibling();
          while (prev instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
          if (prev == null) return true; // there was only whitespace before us
          if (prev instanceof PyStatement || prev instanceof PsiComment) { // a non-stmt would be something strange
            if (prev.getLastChild() instanceof PsiErrorElement) {
              // prev stmt ends with an error. are we on the same line?
              PsiDocumentManager docMgr = PsiDocumentManager.getInstance(p.getProject());
              Document doc = docMgr.getDocument(p.getContainingFile().getOriginalFile());
              if (doc != null) {
                if (doc.getLineNumber(prev.getTextRange().getEndOffset()) == doc.getLineNumber(firstOffset)) {
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
      if (p instanceof PsiComment) return false; // just in case
      int point = p.getTextOffset();
      PsiDocumentManager docMgr = PsiDocumentManager.getInstance(p.getProject());
      final PsiFile file = p.getContainingFile().getOriginalFile();
      Document doc = docMgr.getDocument(file);
      String indentCharacters = file.getViewProvider() instanceof InjectedFileViewProvider? " \t>": " \t";

      if (doc != null) {
        CharSequence chs = doc.getCharsSequence();
        char c;
        do { // scan to the left for a EOL
          point -= 1;
          if (point < 0) return true; // we're at BOF
          c = chs.charAt(point);
          if (c == '\n') return true;
        }
        while (indentCharacters.indexOf(c) >= 0);
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
    psiElement().inside(PyStringLiteralExpression.class).andNot(
      psiElement().inFile(psiFile(PyDocstringFile.class)));

  private static final ElementPattern<PsiElement> IN_FUNCTION_HEADER =
    or(psiElement().inside(PyFunction.class).andNot(psiElement().inside(false, psiElement(PyStatementList.class), psiElement(PyFunction.class))),
       psiElement().inside(PyClass.class).andNot(psiElement().inside(false, psiElement(PyStatementList.class), psiElement(PyClass.class))));

  public static final PsiElementPattern.Capture<PsiElement> AFTER_QUALIFIER =
    psiElement().afterLeaf(psiElement().withText(".").inside(PyReferenceExpression.class));

  public static final FilterPattern FIRST_ON_LINE = new FilterPattern(new StartOfLineFilter());

  private static final PsiElementPattern.Capture<PsiElement> IN_IMPORT_AFTER_REF =
    psiElement()
      .afterLeaf(psiElement().withElementType(PyTokenTypes.IDENTIFIER).inside(PyReferenceExpression.class).inside(PyImportElement.class));

  public static final PsiElementPattern.Capture<PsiElement> IN_FROM_IMPORT_AFTER_REF =
    psiElement().afterLeaf(
      or(psiElement().withElementType(PyTokenTypes.IDENTIFIER).inside(PyReferenceExpression.class),
         psiElement().withElementType(PyTokenTypes.DOT))
      ).inside(PyFromImportStatement.class);

  public static final ElementPattern<PsiElement> IN_WITH_AFTER_REF =
    psiElement().afterLeaf(psiElement().inside(psiElement(PyWithItem.class).with(new PatternCondition<PyWithItem>("withoutAsKeyword") {
      @Override
      public boolean accepts(@NotNull PyWithItem item, ProcessingContext context) {
        return item.getNode().findChildByType(PyTokenTypes.AS_KEYWORD) == null;
      }
    })));

  private static final PsiElementPattern.Capture<PsiElement> IN_EXCEPT_AFTER_REF =
    psiElement().afterLeaf(psiElement()
                             .withElementType(PyTokenTypes.IDENTIFIER)
                             .inside(PyReferenceExpression.class)
                             .inside(PyExceptPart.class)
    );

  private static final PsiElementPattern.Capture<PsiElement> IN_COND_STMT =
    psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyConditionalStatementPart.class)));

  private static final PsiElementPattern.Capture<PsiElement> IN_IF_BODY =
    psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyIfPart.class)));

  private static final PsiElementPattern.Capture<PsiElement> IN_LOOP = 
    psiElement().inside(false, psiElement(PyLoopStatement.class), or(psiElement(PyFunction.class), psiElement(PyClass.class)));

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
      or(psiElement(PyImportStatement.class), psiElement(PyFromImportStatement.class))
    );

  private static final PsiElementPattern.Capture<PsiElement> IN_PARAM_LIST = psiElement().inside(PyParameterList.class);
  private static final PsiElementPattern.Capture<PsiElement> IN_ARG_LIST = psiElement().inside(PyArgumentList.class);

  private static final PsiElementPattern.Capture<PsiElement> IN_DEF_BODY =
    psiElement().inside(false, psiElement(PyFunction.class), psiElement(PyClass.class));

  private static final PsiElementPattern.Capture<PsiElement> IN_TRY_BODY =
    psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyTryPart.class)));

  private static final PsiElementPattern.Capture<PsiElement> IN_EXCEPT_BODY =
    psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyExceptPart.class)));

  private static final PsiElementPattern.Capture<PsiElement> AFTER_IF = afterStatement(psiElement(PyIfStatement.class).withLastChild(
    psiElement(PyIfPart.class)));
  private static final PsiElementPattern.Capture<PsiElement> AFTER_TRY = afterStatement(psiElement(PyTryExceptStatement.class));

  private static final PsiElementPattern.Capture<PsiElement> AFTER_LOOP_NO_ELSE =
    afterStatement(psiElement(PyLoopStatement.class).withLastChild(StandardPatterns.not(psiElement(PyElsePart.class))));

  private static final PsiElementPattern.Capture<PsiElement> AFTER_COND_STMT_NO_ELSE =
    afterStatement(psiElement().withChild(psiElement(PyConditionalStatementPart.class))
      .withLastChild(StandardPatterns.not(psiElement(PyElsePart.class))));

  private static <T extends PsiElement> PsiElementPattern.Capture<PsiElement> afterStatement(final PsiElementPattern.Capture<T> statementPattern) {
    return psiElement().atStartOf(psiElement(PyExpressionStatement.class)
                                    .afterSiblingSkipping(psiElement().whitespaceCommentEmptyOrError(), statementPattern));
  }

  private static final PsiElementPattern.Capture<PsiElement> AFTER_EXCEPT = afterStatement(
    psiElement().withLastChild(psiElement(PyExceptPart.class))
  );

  private static final PsiElementPattern.Capture<PsiElement> IN_FINALLY_NO_LOOP =
    psiElement().inside(false, psiElement(PyFinallyPart.class), psiElement(PyLoopStatement.class));

  private static final FilterPattern IN_BEGIN_STMT = new FilterPattern(new StatementFitFilter());

  /*
  private static final FilterPattern INSIDE_EXPR = new FilterPattern(new PrecededByFilter(
    psiElement(PyExpression.class)
  ));

  private static final FilterPattern INSIDE_EXPR_AFTER_IF = new FilterPattern(new PrecededByFilter(
    psiElement(PyExpression.class).afterLeaf("if")
  ));
  */

  private static final FilterPattern PY3K = new FilterPattern(new Py3kFilter());


  // ======

  private static void putKeywords(final CompletionResultSet result, TailType tail, @NonNls @NotNull String... words) {
    for (String s : words) {
      result.addElement(TailTypeDecorator.withTail(new PythonLookupElement(s, true, null), tail));
    }
  }

  private static void putKeyword(@NotNull @NonNls String keyword, InsertHandler<PythonLookupElement> handler, TailType tail,
                                 CompletionResultSet result) {
    final PythonLookupElement lookupElement = new PythonLookupElement(keyword, true, null);
    lookupElement.setHandler(handler);
    result.addElement(TailTypeDecorator.withTail(lookupElement, tail));
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
        .andNot(BEFORE_COND)
        .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
      ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          putKeywords(result, TailType.NONE, "def", "class", "for", "if", "while", "with");
          putKeywords(result, TailType.CASE_COLON, "try");
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
      .andNot(BEFORE_COND)
      .andNot(AFTER_QUALIFIER);

    extend(
      CompletionType.BASIC,
      inStatement,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          putKeywords(result, TailType.SPACE, "assert", "del", "exec", "from", "import", "raise");
          putKeywords(result, TailType.NONE, PyNames.PASS);
        }
      }
    );

    extend(CompletionType.BASIC, inStatement.andNot(PY3K), new PyKeywordCompletionProvider(TailType.SPACE, "print"));
  }

  private void addBreak() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .and(IN_BEGIN_STMT)
      .andNot(AFTER_QUALIFIER)
      .andNot(IN_PARAM_LIST)
      .andNot(IN_ARG_LIST)
      .and(IN_LOOP)
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
      .and(IN_LOOP)
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
      .andOr(IN_IF_BODY, AFTER_IF)
      .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
      ,
      new PyKeywordCompletionProvider(TailType.NONE, UnindentingInsertHandler.INSTANCE, "elif"));
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
          putKeyword("except", UnindentingInsertHandler.INSTANCE, TailType.NONE, result);
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
      .andOr(IN_COND_STMT, IN_EXCEPT_BODY, AFTER_COND_STMT_NO_ELSE, AFTER_LOOP_NO_ELSE, AFTER_EXCEPT)
      .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
      ,
      new PyKeywordCompletionProvider(TailType.CASE_COLON, UnindentingInsertHandler.INSTANCE, "else"));
  }

  private void addInfixOperators() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .andNot(IN_COMMENT)
      .andNot(BEFORE_COND)
      .andNot(IN_IMPORT_STMT) // expressions there are not logical anyway
      .andNot(IN_PARAM_LIST)
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
      .andNot(IN_FUNCTION_HEADER)
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
      .andNot(IN_FUNCTION_HEADER)
      ,
      new PyKeywordCompletionProvider(TailType.NONE, "True", "False", "None")
    );
  }

  private void addAs() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .andOr(IN_IMPORT_AFTER_REF, IN_WITH_AFTER_REF, IN_EXCEPT_AFTER_REF)
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

  /*
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
  */

  private void addExprElse() {
    extend(
      CompletionType.BASIC, psiElement()
      .withLanguage(PythonLanguage.getInstance())
      .afterLeafSkipping(psiElement().whitespace(),
                         psiElement().inside(psiElement(PyConditionalExpression.class))
                           .and(psiElement().afterLeaf("if")))
      ,
      new PyKeywordCompletionProvider(TailType.SPACE, "else"));
  }

  private void addRaiseFrom() {
    extend(CompletionType.BASIC,
           psiElement()
             .withLanguage(PythonLanguage.getInstance())
             .and(PY3K)
             .afterLeaf(psiElement().inside(PyRaiseStatement.class)),
           new PyKeywordCompletionProvider("from"));
  }

  private void addYieldFrom() {
    extend(CompletionType.BASIC,
           psiElement()
             .withLanguage(PythonLanguage.getInstance())
             .and(PY3K)
             .afterLeaf(psiElement().withElementType(PyTokenTypes.YIELD_KEYWORD)),
           new PyKeywordCompletionProvider("from"));
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
    addRaiseFrom();
    addYieldFrom();
    addForToComprehensions();
    addInToFor();
  }

  private void addForToComprehensions() {
    extend(CompletionType.BASIC,
           psiElement()
             .withLanguage(PythonLanguage.getInstance())
             .inside(psiElement(PySequenceExpression.class))
             .andNot(psiElement().afterLeaf(or(psiElement(PyTokenTypes.LBRACE), psiElement(PyTokenTypes.LBRACKET), psiElement(PyTokenTypes.LPAR)))),
           new PyKeywordCompletionProvider("for"));
  }

  private void addInToFor() {
    extend(CompletionType.BASIC,
           psiElement()
             .withLanguage(PythonLanguage.getInstance())
             .and(psiElement()).afterLeaf(psiElement().afterLeaf("for")),
           new PyKeywordCompletionProvider("in"));

  }

  private static class PyKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final String[] myKeywords;
    private final TailType myTailType;
    private final InsertHandler<PythonLookupElement> myInsertHandler;

    private PyKeywordCompletionProvider(String... keywords) {
      this(TailType.SPACE, keywords);
    }

    private PyKeywordCompletionProvider(TailType tailType, String... keywords) {
      this(tailType, null, keywords);
    }

    private PyKeywordCompletionProvider(TailType tailType, @Nullable InsertHandler<PythonLookupElement> insertHandler, String... keywords) {
      myKeywords = keywords;
      myTailType = tailType;
      myInsertHandler = insertHandler;
    }

    protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context,
                                  @NotNull final CompletionResultSet result) {
      for (String s : myKeywords) {
        final PythonLookupElement element = new PythonLookupElement(s, true, null);
        if (myInsertHandler != null) {
          element.setHandler(myInsertHandler);
        }
        result.addElement(TailTypeDecorator.withTail(element, myTailType));
      }
    }
  }
}
