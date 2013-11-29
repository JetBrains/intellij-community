package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElement;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;

@TemplateInfo(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public final class IfStatementPostfixTemplate extends BooleanPostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PrefixExpressionContext context) {
    if (context.canBeStatement) {
      return new IfLookupItem(context);
    }

    return null;
  }

  static final class IfLookupItem extends ExpressionPostfixLookupElement {
    public IfLookupItem(@NotNull PrefixExpressionContext context) {
      super("if", context);
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

