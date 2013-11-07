package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

public final class PrefixExpressionContext {
  @NotNull public final PostfixTemplateAcceptanceContext parentContext;
  @NotNull public final PsiExpression expression;
  @Nullable public final PsiType expressionType;
  @NotNull public final TextRange expressionRange;
  public final boolean canBeStatement;

  public PrefixExpressionContext(@NotNull final PostfixTemplateAcceptanceContext parentContext,
                                 @NotNull final PsiExpression expression) {
    this.parentContext = parentContext;
    this.expression = expression;
    expressionType = expression.getType();
    expressionRange = getExpressionRange();
    canBeStatement = (getContainingStatement() != null);
  }

  @Nullable public final PsiStatement getContainingStatement() {
    // look for expression-statement parent
    PsiElement parent = expression.getParent();

    // escape from '.postfix' reference-expression
    if (parent == parentContext.postfixReference) {
      parent = parent.getParent();
    }

    if (parent instanceof PsiExpressionStatement) {
      return (PsiStatement) parent;
    }

    return null;
  }

  @NotNull private TextRange getExpressionRange() {
    final TextRange expressionRange = expression.getTextRange();
    final PsiElement reference = parentContext.postfixReference;

    PsiElement qualifier = null;

    if (reference instanceof PsiReferenceExpression) {
      // fix range from 'a > b.if' to 'a > b'
      qualifier = ((PsiReferenceExpression) reference).getQualifierExpression();
    } else if (reference instanceof PsiJavaCodeReferenceElement) {
      // fix range from 'o instanceof T.if' to 'o instanceof T'
      qualifier = ((PsiJavaCodeReferenceElement) reference).getQualifier();
    }

    if (qualifier != null && qualifier.isValid()) {
      final int qualifierEndRange = qualifier.getTextRange().getEndOffset();
      if (expressionRange.getEndOffset() > qualifierEndRange) {
        return new TextRange(expressionRange.getStartOffset(), qualifierEndRange);
      }
    }

    return expressionRange;
  }

  @NotNull public final PrefixExpressionContext fixUp() {
    return parentContext.fixUpExpression(this);
  }
}