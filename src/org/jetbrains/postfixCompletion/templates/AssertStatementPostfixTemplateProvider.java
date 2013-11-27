package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;

import java.util.List;

@TemplateProvider(
  templateName = "assert",
  description = "Creates assertion from boolean expression",
  example = "assert expr;")
public final class AssertStatementPostfixTemplateProvider extends BooleanPostfixTemplateProvider {
  @Override public boolean createBooleanItems(
      @NotNull PrefixExpressionContext context, @NotNull List<LookupElement> consumer) {
    if (context.canBeStatement) {
      consumer.add(new AssertLookupElement(context));
      return true;
    }

    return false;
  }

  static final class AssertLookupElement extends StatementPostfixLookupElement<PsiAssertStatement> {
    public AssertLookupElement(@NotNull PrefixExpressionContext expression) {
      super("assert", expression);
    }

    @NotNull @Override protected PsiAssertStatement createNewStatement(
        @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      PsiAssertStatement assertStatement =
        (PsiAssertStatement) factory.createStatementFromText("assert expr;", expression);

      PsiExpression condition = assertStatement.getAssertCondition();
      assert (condition != null) : "condition != null";
      condition.replace(expression);

      return assertStatement;
    }
  }
}