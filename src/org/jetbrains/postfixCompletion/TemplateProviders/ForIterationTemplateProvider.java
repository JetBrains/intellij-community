package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

// TODO: int i = 1 + 2.for + 3 + 4 + 5 - why available?

@TemplateProvider(
  templateName = "for",
  description = "Iterates over enumerable collection",
  example = "for (T item : collection)")
public class ForIterationTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {

    PrefixExpressionContext expression = context.outerExpression;

    PsiType type = expression.expressionType;
    if (type != null && !context.executionContext.isForceMode) {
      // check type to be Iterable-derived or array type
      if (!(type instanceof PsiArrayType) &&
          !InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)) return;
    }

    consumer.add(new ForeachLookupElement(expression));
  }

  private static final class ForeachLookupElement extends StatementPostfixLookupElement<PsiExpressionStatement> {
    public ForeachLookupElement(@NotNull PrefixExpressionContext context) {
      super("for", context);
    }

    @NotNull @Override protected PsiExpressionStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {

      PsiExpressionStatement statement = (PsiExpressionStatement) factory.createStatementFromText("expr", context);
      statement.getExpression().replace(expression);

      return statement;
    }

    @Override protected void postProcess(
      @NotNull InsertionContext context, @NotNull PsiExpressionStatement statement) {

      TemplateImpl template = TemplateSettings.getInstance().getTemplate("I", "surround");

      TextRange textRange = statement.getExpression().getTextRange();

      SelectionModel selectionModel = context.getEditor().getSelectionModel();
      selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());

      new InvokeTemplateAction(
        template, context.getEditor(), context.getProject(),
        new HashSet<Character>()).perform();
    }
  }
}