package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

@TemplateProvider(
  templateName = "fori",
  description = "Iterates with index over collection",
  example = "for (int i = 0; i < expr.length; i++)")
public final class ForIndexedIterationTemplateProvider extends ForIterationTemplateProviderBase {
  @Override @NotNull protected ForIndexedLookupElement createIterationLookupElement(
    @NotNull PrefixExpressionContext expression, @NotNull String indexVarType, @NotNull String sizeAccessSuffix) {

    return new ForIndexedLookupElement(expression, indexVarType, sizeAccessSuffix);
  }

  private static final class ForIndexedLookupElement extends ForLookupElementBase {
    public ForIndexedLookupElement(
        @NotNull PrefixExpressionContext context,
        @NotNull String indexVariableType, @NotNull String sizeAccessSuffix) {
      super("fori", context,
        "for(" + indexVariableType + " i=0;i<expr" + sizeAccessSuffix + ";i++)");
    }

    @NotNull protected PsiExpression findBoundExpression(@NotNull PsiForStatement forStatement) {
      PsiBinaryExpression condition = (PsiBinaryExpression) forStatement.getCondition();
      assert (condition != null) : "condition != null";

      PsiExpression boundExpression = condition.getROperand();
      boundExpression = unwrapExpression(boundExpression);

      return boundExpression;
    }
  }
}