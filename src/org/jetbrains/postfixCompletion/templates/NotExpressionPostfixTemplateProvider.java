package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.Infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.LookupItems.ExpressionPostfixLookupElement;

import java.util.List;

@TemplateProvider(
  templateName = "not",
  description = "Negates boolean expression",
  example = "!expr",
  worksInsideFragments = true)
public final class NotExpressionPostfixTemplateProvider extends BooleanPostfixTemplateProvider {
  @Override public boolean createBooleanItems(
      @NotNull PrefixExpressionContext context, @NotNull List<LookupElement> consumer) {
    consumer.add(new NotExpressionLookupElement(context));
    return true;
  }

  private static final class NotExpressionLookupElement extends ExpressionPostfixLookupElement<PsiExpression> {
    public NotExpressionLookupElement(@NotNull PrefixExpressionContext context) {
      super("not", context);
    }

    @NotNull @Override protected PsiExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      return CodeInsightServicesUtil.invertCondition((PsiExpression) expression);
    }

    @Override protected void postProcess(
        @NotNull InsertionContext context, @NotNull final PsiExpression expression) {
      // collapse '!!b' into 'b'
      if (isUnaryNegation(expression)) {
        final PsiExpression operand = ((PsiPrefixExpression) expression).getOperand();
        final PsiElement parent = expression.getParent();
        if (operand != null && isUnaryNegation(parent)) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override public void run() {
              parent.replace(operand);
            }
          });
          return;
        }
      }

      super.postProcess(context, expression);
    }

    private boolean isUnaryNegation(@Nullable PsiElement element) {
      return (element instanceof PsiPrefixExpression)
          && ((PsiPrefixExpression) element).getOperationTokenType() == JavaTokenType.EXCL;
    }
  }
}
