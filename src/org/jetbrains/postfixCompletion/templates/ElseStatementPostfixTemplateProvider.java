package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.CodeInsightServicesUtil;
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
  templateName = "else",
  description = "Checks boolean expression to be 'false'",
  example = "if (!expr)")
public final class ElseStatementPostfixTemplateProvider extends BooleanPostfixTemplateProvider {
  @Override public boolean createBooleanItems(
    @NotNull PrefixExpressionContext context, @NotNull List<LookupElement> consumer) {
    if (context.canBeStatement) {
      consumer.add(new ElseLookupItem(context));
      return true;
    }

    return false;
  }

  static final class ElseLookupItem extends IfStatementPostfixLookupItem {
    public ElseLookupItem(@NotNull PrefixExpressionContext context) {
      super("else", context);
    }

    @Override protected void processStatement(
        @NotNull PsiElementFactory factory, @NotNull PsiIfStatement ifStatement, @NotNull PsiElement expression) {
      PsiExpression condition = ifStatement.getCondition();
      assert (condition != null) : "condition != null";

      PsiExpression inverted = CodeInsightServicesUtil.invertCondition((PsiExpression) expression);
      condition.replace(inverted);
    }
  }
}
