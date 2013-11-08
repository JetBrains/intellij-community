package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public final class IfStatementTemplateProvider extends BooleanTemplateProviderBase {
  @Override public boolean createBooleanItems(
    @NotNull PrefixExpressionContext context, @NotNull List<LookupElement> consumer) {

    if (context.canBeStatement) {
      consumer.add(new IfLookupItem(context));
      return true;
    }

    return false;
  }

  static final class IfLookupItem extends StatementPostfixLookupElement<PsiIfStatement> {
    public IfLookupItem(@NotNull PrefixExpressionContext context) {
      super("if", context);
    }

    @NotNull @Override protected PsiIfStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiFile context) {

      PsiIfStatement ifStatement = (PsiIfStatement) factory.createStatementFromText("if(expr)", context);

      PsiExpression condition = ifStatement.getCondition();
      assert condition != null : "condition != null";

      condition.replace(expression);

      return ifStatement;
    }

    @Override protected void postProcess(
      @NotNull InsertionContext context, @NotNull PsiIfStatement statement) {

      PsiJavaToken rParenth = statement.getRParenth();
      assert rParenth != null : "rParenth != null";

      int offset = rParenth.getTextRange().getEndOffset();
      context.getEditor().getCaretModel().moveToOffset(offset);
    }
  }
}

