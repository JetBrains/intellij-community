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
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElementBase;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;

@TemplateInfo(
  templateName = "else",
  description = "Checks boolean expression to be 'false'",
  example = "if (!expr)")
public final class ElseStatementPostfixTemplate extends BooleanPostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PrefixExpressionContext context) {
    if (context.canBeStatement) {
      return new ElseLookupItem(context);
    }

    return null;
  }

  static final class ElseLookupItem extends ExpressionPostfixLookupElementBase<PsiExpression> {
    public ElseLookupItem(@NotNull PrefixExpressionContext context) {
      super("else", context);
    }

    @NotNull
    @Override
    protected PsiExpression createNewExpression(@NotNull PsiElementFactory factory,
                                                @NotNull PsiElement expression,
                                                @NotNull PsiElement context) {
      return CodeInsightServicesUtil.invertCondition((PsiExpression)expression);
    }

    @Override
    protected void postProcess(@NotNull InsertionContext context, @NotNull PsiExpression expression) {
      TextRange range = JavaSurroundersProxy.ifStatement(context.getProject(), context.getEditor(), expression);
      if (range != null) {
        context.getEditor().getCaretModel().moveToOffset(range.getStartOffset());
      }
    }
  }
}
