package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElement;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;
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

  static final class ElseLookupItem extends ExpressionPostfixLookupElement {
    public ElseLookupItem(@NotNull PrefixExpressionContext context) {
      super("else", context);
    }

    @NotNull @Override protected PsiExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      return CodeInsightServicesUtil.invertCondition((PsiExpression) expression);
    }

    @Override protected void postProcess(@NotNull InsertionContext context, @NotNull PsiExpression expression) {
      TextRange range = JavaSurroundersProxy.ifStatement(context.getProject(), context.getEditor(), expression);
      if (range != null) {
        context.getEditor().getCaretModel().moveToOffset(range.getStartOffset());
      }
    }
  }
}
