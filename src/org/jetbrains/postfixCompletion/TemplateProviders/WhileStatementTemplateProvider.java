package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "while",
  description = "Iterating while boolean statement is 'true'",
  example = "while (expr)")
public final class WhileStatementTemplateProvider extends BooleanTemplateProviderBase {
  @Override public boolean createBooleanItems(
    @NotNull PrefixExpressionContext context, @NotNull List<LookupElement> consumer) {

    if (context.canBeStatement) {
      consumer.add(new WhileLookupItem(context));
      return true;
    }

    return false;
  }

  static final class WhileLookupItem extends StatementPostfixLookupElement<PsiWhileStatement> {
    public WhileLookupItem(@NotNull PrefixExpressionContext context) {
      super("while", context);
    }

    @NotNull @Override protected PsiWhileStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {

      PsiWhileStatement whileStatement = (PsiWhileStatement) factory.createStatementFromText("while(expr)", context);

      PsiExpression condition = whileStatement.getCondition();
      assert condition != null : "condition != null";

      condition.replace(expression);

      return whileStatement;
    }

    @Override protected void postProcess(@NotNull InsertionContext context, @NotNull PsiWhileStatement statement) {
      PsiJavaToken rParenth = statement.getRParenth();
      assert rParenth != null : "rParenth != null";

      int offset = rParenth.getTextRange().getEndOffset();
      context.getEditor().getCaretModel().moveToOffset(offset);
    }
  }
}

