package com.intellij.tasks.jira.jql.codeinsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.tasks.jira.jql.JqlTokenTypes;
import com.intellij.tasks.jira.jql.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Mikhail Golubev
 */
public class JqlCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(JqlCompletionContributor.class);

  private static final FilterPattern BEGINNING_OF_LINE = new FilterPattern(new ElementFilter() {
    @Override
    public boolean isAcceptable(Object element, @Nullable PsiElement context) {
      if (!(element instanceof PsiElement)) return false;
      PsiElement p = (PsiElement)element;
      PsiFile file = p.getContainingFile().getOriginalFile();
      char[] chars = file.textToCharArray();
      for (int offset = p.getTextOffset() - 1; offset >= 0; offset--) {
        char c = chars[offset];
        if (c == '\n') return true;
        if (!StringUtil.isWhiteSpace(c)) return false;
      }
      return true;
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  });

  private static FilterPattern rightAfterElement(final PsiElementPattern.Capture<? extends PsiElement> pattern) {
    return new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (!(element instanceof PsiElement)) return false;
        PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf((PsiElement)element);
        if (prevLeaf == null) return false;
        PsiElement parent = PsiTreeUtil.findFirstParent(prevLeaf, new Condition<PsiElement>() {
          @Override
          public boolean value(PsiElement element) {
            return pattern.accepts(element);
          }
        });
        if (parent == null) return false;
        if (PsiTreeUtil.hasErrorElements(parent)) return false;
        return prevLeaf.getTextRange().getEndOffset() == parent.getTextRange().getEndOffset();
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    });
  }

  private static FilterPattern rightAfterElement(Class<? extends PsiElement> aClass) {
    return rightAfterElement(psiElement(aClass));
  }

  // Patterns:

  private static final PsiElementPattern.Capture<PsiElement> AFTER_CLAUSE_WITH_HISTORY_PREDICATE =
    psiElement().and(rightAfterElement(JqlClauseWithHistoryPredicates.class));

  private static final PsiElementPattern.Capture<PsiElement> AFTER_ANY_CLAUSE =
    psiElement().andOr(
      rightAfterElement(JqlTerminalClause.class),
      // in other words after closing parenthesis
      rightAfterElement(JqlSubClause.class));

  private static final PsiElementPattern.Capture<PsiElement> AFTER_ORDER_KEYWORD =
    psiElement().afterLeaf(psiElement(JqlTokenTypes.ORDER_KEYWORD));

  private static final PsiElementPattern.Capture<PsiElement> AFTER_FIELD_IN_CLAUSE =
    psiElement().and(rightAfterElement(
      psiElement(JqlIdentifier.class).
        andNot(psiElement().inside(JqlFunctionCall.class)).
        andNot(psiElement().inside(JqlOrderBy.class))));


  /**
   * e.g. "not | ...", "status = closed and |" or "status = closed or |"
   */
  private static final PsiElementPattern.Capture<PsiElement> BEGINNING_OF_CLAUSE = psiElement().andOr(
    BEGINNING_OF_LINE,
    psiElement().afterLeaf(psiElement().andOr(
      psiElement().withElementType(JqlTokenTypes.AND_OPERATORS),
      psiElement().withElementType(JqlTokenTypes.OR_OPERATORS),
      psiElement().withElementType(JqlTokenTypes.NOT_OPERATORS).
        andNot(psiElement().inside(JqlTerminalClause.class)),
      psiElement().withElementType(JqlTokenTypes.LPAR).
        andNot(psiElement().inside(JqlTerminalClause.class))
    )));

  /**
   * e.g. "status changed on |"
   */
  private static final PsiElementPattern.Capture<PsiElement> AFTER_KEYWORD_IN_HISTORY_PREDICATE = psiElement().
    inside(JqlHistoryPredicate.class). // do not consider "by" inside "order by"
    afterLeaf(psiElement().withElementType(JqlTokenTypes.HISTORY_PREDICATES));

  /**
   * e.g. "duedate > |" or "type was in |"
   */
  private static final PsiElementPattern.Capture<PsiElement> AFTER_OPERATOR_EXCEPT_IS = psiElement().
    inside(JqlTerminalClause.class).
    afterLeaf(
      psiElement().andOr(
        psiElement().withElementType(JqlTokenTypes.SIMPLE_OPERATORS),
        psiElement(JqlTokenTypes.WAS_KEYWORD),
        psiElement(JqlTokenTypes.IN_KEYWORD),
        // "not" is considered only as part of other complex operators
        // "is" and "is not" are not suitable also
        psiElement(JqlTokenTypes.NOT_KEYWORD).
          afterLeaf(psiElement(JqlTokenTypes.WAS_KEYWORD))));
  /**
   * e.g. "foo is |" or "foo is not |"
   */
  private static final PsiElementPattern.Capture<PsiElement> AFTER_IS_OPERATOR = psiElement().
    inside(JqlTerminalClause.class).andOr(
    psiElement().afterLeaf(psiElement(JqlTokenTypes.IS_KEYWORD)),
    psiElement().afterLeaf(psiElement(JqlTokenTypes.NOT_KEYWORD).
      afterLeaf(psiElement(JqlTokenTypes.IS_KEYWORD)))
  );

  /**
   * e.g. "commentary ~ 'spam' order by |" or "assignee = currentUser() order by duedate desc, |"
   */
  private static final PsiElementPattern.Capture<PsiElement> BEGINNING_OF_SORT_KEY = psiElement().
    inside(JqlOrderBy.class).
    andOr(
      psiElement().afterLeaf(psiElement(JqlTokenTypes.COMMA)),
      psiElement().afterLeaf(psiElement(JqlTokenTypes.BY_KEYWORD))
    );

  /**
   * e.g. "status = 'in progress' order by reported |"
   */
  private static final PsiElementPattern.Capture<PsiElement> AFTER_FIELD_IN_SORT_KEY = psiElement().
    afterLeaf(psiElement().withElementType(JqlTokenTypes.VALID_FIELD_NAMES).inside(JqlSortKey.class));

  private static final PsiElementPattern.Capture<PsiElement> INSIDE_LIST = psiElement().
    inside(JqlList.class).
    afterLeaf(
      psiElement().andOr(
        psiElement(JqlTokenTypes.LPAR),
        psiElement(JqlTokenTypes.COMMA)
        // e.g. assignee in ('mark', 'bob', currentUser() | )
      ).andNot(psiElement().inside(JqlFunctionCall.class))
    );

  public JqlCompletionContributor() {
    addKeywordsCompletion();
    addFieldNamesCompletion();
    addFunctionNamesCompletion();
    addEmptyOrNullCompletion();
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    LOG.debug(DebugUtil.psiToString(parameters.getOriginalFile(), true));
    super.fillCompletionVariants(parameters, result);
  }

  private void addKeywordsCompletion() {
    extend(CompletionType.BASIC,
           AFTER_ANY_CLAUSE,
           new JqlKeywordCompletionProvider("and", "or", "order by"));
    extend(CompletionType.BASIC,
           AFTER_CLAUSE_WITH_HISTORY_PREDICATE,
           new JqlKeywordCompletionProvider("on", "before", "after", "during", "from", "to", "by"));
    extend(CompletionType.BASIC,
           AFTER_FIELD_IN_CLAUSE,
           new JqlKeywordCompletionProvider("was", "in", "not", "is", "changed"));
    extend(CompletionType.BASIC,
           psiElement().andOr(
             BEGINNING_OF_CLAUSE,
             psiElement().inside(JqlTerminalClause.class).andOr(
               psiElement().afterLeaf(psiElement(JqlTokenTypes.WAS_KEYWORD)),
               psiElement().afterLeaf(psiElement(JqlTokenTypes.IS_KEYWORD)))),
           new JqlKeywordCompletionProvider("not"));
    extend(CompletionType.BASIC,
           psiElement().afterLeaf(
             psiElement().andOr(
               psiElement(JqlTokenTypes.NOT_KEYWORD).
                 andNot(psiElement().afterLeaf(
                   psiElement(JqlTokenTypes.IS_KEYWORD))).
                 andNot(psiElement().withParent(JqlNotClause.class)),
               psiElement(JqlTokenTypes.WAS_KEYWORD))),
           new JqlKeywordCompletionProvider("in"));
    extend(CompletionType.BASIC,
           AFTER_ORDER_KEYWORD,
           new JqlKeywordCompletionProvider("by"));
    extend(CompletionType.BASIC,
           AFTER_FIELD_IN_SORT_KEY,
           new JqlKeywordCompletionProvider("asc", "desc"));
  }

  private void addFieldNamesCompletion() {
    extend(CompletionType.BASIC,
           psiElement().andOr(
             BEGINNING_OF_CLAUSE,
             BEGINNING_OF_SORT_KEY),
           new JqlFieldCompletionProvider(JqlFieldType.UNKNOWN));
  }

  private void addFunctionNamesCompletion() {
    extend(CompletionType.BASIC,
           psiElement().andOr(
             AFTER_OPERATOR_EXCEPT_IS,
             INSIDE_LIST,
             // NOTE: function calls can't be used as other functions arguments according to grammar
             AFTER_KEYWORD_IN_HISTORY_PREDICATE),
           new JqlFunctionCompletionProvider());
  }

  private void addEmptyOrNullCompletion() {
    extend(CompletionType.BASIC,
           AFTER_IS_OPERATOR,
           new JqlKeywordCompletionProvider("empty", "null"));
  }

  private static class JqlKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final String[] myKeywords;

    private JqlKeywordCompletionProvider(String... keywords) {
      myKeywords = keywords;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      for (String keyword : myKeywords) {
        result.addElement(LookupElementBuilder.create(keyword).withBoldness(true));
      }
    }
  }

  private static class JqlFunctionCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      JqlFieldType operandType;
      boolean listFunctionExpected;
      PsiElement curElem = parameters.getPosition();
      JqlHistoryPredicate predicate = PsiTreeUtil.getParentOfType(curElem, JqlHistoryPredicate.class);
      if (predicate != null) {
        listFunctionExpected = false;
        JqlHistoryPredicate.Type predicateType = predicate.getType();
        switch (predicateType) {
          case BEFORE:
          case AFTER:
          case DURING:
          case ON:
            operandType = JqlFieldType.DATE;
            break;
          case BY:
            operandType = JqlFieldType.USER;
            break;
          // from, to
          default:
            operandType = findTypeOfField(curElem);
        }
      }
      else {
        operandType = findTypeOfField(curElem);
        listFunctionExpected = insideClauseWithListOperator(curElem);
      }
      for (String functionName : JqlStandardFunction.allOfType(operandType, listFunctionExpected)) {
        result.addElement(LookupElementBuilder.create(functionName)
          .withInsertHandler(ParenthesesInsertHandler.NO_PARAMETERS));
      }
    }

    private static JqlFieldType findTypeOfField(PsiElement element) {
      JqlTerminalClause clause = PsiTreeUtil.getParentOfType(element, JqlTerminalClause.class);
      if (clause != null) {
        return JqlStandardField.typeOf(clause.getFieldName());
      }
      return JqlFieldType.UNKNOWN;
    }

    private static boolean insideClauseWithListOperator(PsiElement element) {
      JqlTerminalClause clause = PsiTreeUtil.getParentOfType(element, JqlTerminalClause.class);
      if (clause == null || clause.getType() == null) {
        return false;
      }
      return clause.getType().isListOperator();
    }
  }

  private static class JqlFieldCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final JqlFieldType myFieldType;

    private JqlFieldCompletionProvider(JqlFieldType fieldType) {
      myFieldType = fieldType;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      for (String field : JqlStandardField.allOfType(myFieldType)) {
        result.addElement(LookupElementBuilder.create(field));
      }
    }
  }
}
