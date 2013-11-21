package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

@TemplateProvider(
  templateName = "forr",
  description = "Iterates with index over collection in reverse order",
  example = "for (int i = expr.Length - 1; i >= 0; i--)")
public final class ForReverseIterationTemplateProvider extends ForIterationTemplateProviderBase {
  @Override @NotNull protected ForReverseLookupElement createIterationLookupElement(
    @NotNull PrefixExpressionContext expression, @NotNull String indexVarType, @NotNull String sizeAccessSuffix) {

    return new ForReverseLookupElement(expression, indexVarType, sizeAccessSuffix);
  }

  private static final class ForReverseLookupElement extends ForLookupElementBase {
    public ForReverseLookupElement(
        @NotNull PrefixExpressionContext context,
        @NotNull String indexVariableType, @NotNull String sizeAccessSuffix) {
      super("forr", context,
        sizeAccessSuffix.equals("") // a bit nicer loop over integral values
          ? "for(" + indexVariableType + " i=expr" + sizeAccessSuffix + ";i>0;i--)"
          : "for(" + indexVariableType + " i=expr" + sizeAccessSuffix + "-1;i>=0;i--)");
    }

    @NotNull protected PsiExpression findBoundExpression(@NotNull PsiForStatement forStatement) {
      PsiDeclarationStatement initialization = (PsiDeclarationStatement) forStatement.getInitialization();
      assert (initialization != null) : "initialization != null";

      PsiLocalVariable indexVariable = (PsiLocalVariable) initialization.getDeclaredElements()[0];
      PsiExpression boundExpression = indexVariable.getInitializer();
      assert (boundExpression != null) : "boundExpression != null";

      if (boundExpression instanceof PsiBinaryExpression) {
        boundExpression = ((PsiBinaryExpression) boundExpression).getLOperand();
      }

      boundExpression = unwrapExpression(boundExpression);
      return boundExpression;
    }
  }
}