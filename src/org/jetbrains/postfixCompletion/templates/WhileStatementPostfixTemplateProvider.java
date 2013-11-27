package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.LookupItems.StatementPostfixLookupElement;

import java.util.List;

@TemplateProvider(
  templateName = "while",
  description = "Iterating while boolean statement is 'true'",
  example = "while (expr)")
public final class WhileStatementPostfixTemplateProvider extends BooleanPostfixTemplateProvider {
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
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {

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

