package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIfStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.lookupItems.IfStatementPostfixLookupItem;

import java.util.List;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public final class IfStatementPostfixTemplateProvider extends BooleanPostfixTemplateProvider {
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
        @NotNull PsiElementFactory factory, @NotNull PsiIfStatement ifStatement, @NotNull PsiElement expression) {
      PsiExpression condition = ifStatement.getCondition();
      assert (condition != null) : "condition != null";

      condition.replace(expression);
    }
  }
}

