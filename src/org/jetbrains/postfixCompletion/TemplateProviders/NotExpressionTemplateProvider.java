package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "not",
  description = "Negates boolean expression",
  example = "!expr")
public final class NotExpressionTemplateProvider extends BooleanTemplateProviderBase {
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
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {

      return CodeInsightServicesUtil.invertCondition(expression);
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
      return element instanceof PsiPrefixExpression &&
        ((PsiPrefixExpression) element).getOperationTokenType() == JavaTokenType.EXCL;
    }
  }
}
