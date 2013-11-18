package org.jetbrains.postfixCompletion.TemplateProviders;

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

  static final class IfLookupItem extends IfStatementPostfixLookupItem {
    public IfLookupItem(@NotNull PrefixExpressionContext context) {
      super("if", context);
    }

    @Override protected void processStatement(
        @NotNull PsiElementFactory factory, @NotNull PsiIfStatement ifStatement, @NotNull PsiExpression expression) {
      PsiExpression condition = ifStatement.getCondition();
      assert condition != null : "condition != null";

      condition.replace(expression);
    }
  }
}

