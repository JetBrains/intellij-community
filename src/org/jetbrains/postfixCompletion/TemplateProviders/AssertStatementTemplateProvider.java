package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "assert",
  description = "Creates assertion from boolean expression",
  example = "assert expr;")
public final class AssertStatementTemplateProvider extends BooleanTemplateProviderBase {
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