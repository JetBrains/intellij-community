package org.jetbrains.postfixCompletion.templates;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateProvider;

@TemplateProvider(
  templateName = "forr",
  description = "Iterates with index in reverse order",
  example = "for (int i = expr.length-1; i >= 0; i--)")
public final class ForReverseIterationPostfixTemplateProvider extends ForIterationPostfixTemplateProvider {
  @Override
  @NotNull
  protected ForReverseLookupElement createIterationLookupElement(@NotNull PrefixExpressionContext expression,
                                                                 @NotNull String indexVarType,
                                                                 @NotNull String sizeAccessSuffix) {
    return new ForReverseLookupElement(expression, indexVarType, sizeAccessSuffix);
  }

  private static final class ForReverseLookupElement extends ForLookupElementBase {
    public ForReverseLookupElement(@NotNull PrefixExpressionContext context,
                                   @NotNull String indexVariableType,
                                   @NotNull String sizeAccessSuffix) {
      super("forr", context, sizeAccessSuffix.isEmpty() // a bit nicer loop over integral values
        ? "for(" + indexVariableType + " i=expr" + sizeAccessSuffix + ";i>0;i--)"
        : "for(" + indexVariableType + " i=expr" + sizeAccessSuffix + "-1;i>=0;i--)");
    }

    @NotNull
    protected PsiExpression findBoundExpression(@NotNull PsiForStatement forStatement) {
      PsiDeclarationStatement initialization = (PsiDeclarationStatement)forStatement.getInitialization();
      assert initialization != null;

      PsiLocalVariable indexVariable = (PsiLocalVariable)initialization.getDeclaredElements()[0];
      PsiExpression boundExpression = indexVariable.getInitializer();
      assert boundExpression != null;

      if (boundExpression instanceof PsiBinaryExpression) {
        boundExpression = ((PsiBinaryExpression)boundExpression).getLOperand();
      }

      boundExpression = unwrapExpression(boundExpression);
      return boundExpression;
    }
  }
}