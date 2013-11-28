package org.jetbrains.postfixCompletion.infrastructure;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixExpressionContext {
  @NotNull public final PostfixTemplateContext parentContext;
  @NotNull public final PsiElement expression;
  @Nullable public final PsiType expressionType;
  @Nullable public final PsiElement referencedElement;
  @NotNull public final TextRange expressionRange;
  public final boolean canBeStatement;

  public PrefixExpressionContext(
      @NotNull PostfixTemplateContext parentContext, @NotNull PsiElement expression) {
    assert expression.isValid() : "expression.isValid()";

    this.parentContext = parentContext;
    this.expression = expression;
    this.expressionType = calculateExpressionType(expression);
    this.referencedElement = calculateReferencedElement(expression);

    expressionRange = calculateExpressionRange();
    canBeStatement = (getContainingStatement() != null);
  }

  @Nullable public final PsiStatement getContainingStatement() {
    return parentContext.getContainingStatement(this);
  }

  @Nullable protected PsiType calculateExpressionType(@NotNull PsiElement expression) {
    if (expression instanceof PsiExpression) {
      return ((PsiExpression) expression).getType();
    }

    return null;
  }

  @Nullable protected PsiElement calculateReferencedElement(@NotNull PsiElement expression) {
    if (expression instanceof PsiJavaCodeReferenceElement) {
      return ((PsiJavaCodeReferenceElement) expression).resolve();
    }

    return null;
  }

  @NotNull protected TextRange calculateExpressionRange() {
    TextRange expressionRange = expression.getTextRange();
    PsiJavaCodeReferenceElement reference = parentContext.postfixReference;

    // fix range from 'a > b.if' to 'a > b' or from 'o instanceof T.if' to 'o instanceof T'
    PsiElement qualifier = reference.getQualifier();

    if (qualifier != null && qualifier.isValid()) {
      int qualifierEndRange = qualifier.getTextRange().getEndOffset();
      if (expressionRange.getEndOffset() > qualifierEndRange)
        return new TextRange(expressionRange.getStartOffset(), qualifierEndRange);
    }

    return expressionRange;
  }

  @NotNull public final PrefixExpressionContext fixExpression() {
    PrefixExpressionContext fixedContext = parentContext.fixExpression(this);
    PsiElement fixedExpression = fixedContext.expression;

    assert fixedExpression.isPhysical() : "fixedExpression.isPhysical()";

    return fixedContext;
  }
}