package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

public final class PrefixExpressionContext {
  @NotNull public final PostfixTemplateAcceptanceContext parentContext;
  @NotNull public final PsiExpression expression;
  @Nullable public final PsiType expressionType;
  @Nullable public final PsiElement referencedElement;
  @NotNull public final TextRange expressionRange;
  public final boolean canBeStatement;

  public PrefixExpressionContext(
      @NotNull PostfixTemplateAcceptanceContext parentContext, @NotNull PsiExpression expression) {
    this.parentContext = parentContext;
    this.expression = expression;
    expressionType = expression.getType();
    expressionRange = getExpressionRange();
    canBeStatement = (getContainingStatement() != null);

    if (expression instanceof PsiReferenceExpression) {
      referencedElement = ((PsiReferenceExpression) expression).resolve();
    } else {
      referencedElement = null;
    }
  }

  @Nullable public final PsiStatement getContainingStatement() {
    // look for expression-statement parent
    PsiElement element = expression.getParent();

    // escape from '.postfix' reference-expression
    if (element == parentContext.postfixReference) {

      // sometimes IDEA idea completion breaks expression in the middle into statement
      if (element instanceof PsiReferenceExpression) {
        // check we are invoked from code completion
        String referenceName = ((PsiReferenceExpression) element).getReferenceName();
        if (referenceName != null && referenceName.endsWith(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
          PsiElement referenceParent = element.getParent(); // find separated expression-statement
          if (referenceParent instanceof PsiExpressionStatement) {
            PsiElement nextSibling = referenceParent.getNextSibling();
            if (nextSibling instanceof PsiExpressionStatement) { // find next expression-statement
              PsiExpression brokenExpression = ((PsiExpressionStatement) nextSibling).getExpression();
              // check next expression is likely broken invocation expression
              if (brokenExpression instanceof PsiParenthesizedExpression) return null;
              if (brokenExpression instanceof PsiMethodCallExpression) return null;
            }
          }
        }
      }

      element = element.getParent();
    }

    if (element instanceof PsiExpressionStatement) {
      return (PsiStatement) element;
    }

    return null;
  }

  @NotNull private TextRange getExpressionRange() {
    TextRange expressionRange = expression.getTextRange();
    PsiElement reference = parentContext.postfixReference, qualifier = null;

    if (reference instanceof PsiReferenceExpression) {
      // fix range from 'a > b.if' to 'a > b'
      qualifier = ((PsiReferenceExpression) reference).getQualifierExpression();
    } else if (reference instanceof PsiJavaCodeReferenceElement) {
      // fix range from 'o instanceof T.if' to 'o instanceof T'
      qualifier = ((PsiJavaCodeReferenceElement) reference).getQualifier();
    }

    if (qualifier != null && qualifier.isValid()) {
      int qualifierEndRange = qualifier.getTextRange().getEndOffset();
      if (expressionRange.getEndOffset() > qualifierEndRange)
        return new TextRange(expressionRange.getStartOffset(), qualifierEndRange);
    }

    return expressionRange;
  }

  @NotNull public final PrefixExpressionContext fixUp() {
    PrefixExpressionContext fixedContext = parentContext.fixUpExpression(this);
    assert fixedContext.expression.isPhysical() : "fixedContext.expression.isPhysical()";

    return fixedContext;
  }
}