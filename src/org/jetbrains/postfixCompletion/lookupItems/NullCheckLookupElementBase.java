package org.jetbrains.postfixCompletion.lookupItems;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;

public abstract class NullCheckLookupElementBase extends ExpressionPostfixLookupElementBase<PsiExpression> {
  public NullCheckLookupElementBase(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  @NotNull @Override protected PsiExpression createNewExpression(
    @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
    PsiBinaryExpression condition = (PsiBinaryExpression) factory.createExpressionFromText(getConditionText(), context);
    condition.getLOperand().replace(expression);

    return condition;
  }

  @NotNull protected abstract String getConditionText();

  @Override protected void postProcess(@NotNull final InsertionContext context, @NotNull final PsiExpression expression) {
    final Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      surround(context, expression);
    }
    else {
      // I'm using this shit just to workaround code completion tail watcher assertion in tests :(
      context.setLaterRunnable(new Runnable() {
        @Override public void run() {
          application.runWriteAction(new Runnable() {
            @Override public void run() { surround(context, expression); }
          });
        }
      });
    }
  }

  private static void surround(@NotNull InsertionContext context, @NotNull PsiExpression expression) {
    TextRange range = JavaSurroundersProxy.ifStatement(context.getProject(), context.getEditor(), expression);
    if (range != null) {
      context.getEditor().getCaretModel().moveToOffset(range.getStartOffset());
    }
  }
}