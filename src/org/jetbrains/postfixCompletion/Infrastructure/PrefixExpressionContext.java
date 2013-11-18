package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

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
    // look for expression-statement parent
    PsiElement element = expression.getParent();

    // escape from '.postfix' reference-expression
    if (element == parentContext.postfixReference) {
      // sometimes IDEA's code completion breaks expression in the middle into statement
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
              if (brokenExpression instanceof PsiParenthesizedExpression) return null; // foo;();
              if (brokenExpression instanceof PsiMethodCallExpression) return null;    // fo;o();
            }
          }
        }
      }

      element = element.getParent();
    }

    if (element instanceof PsiExpressionStatement) {
      // ignore expression-statements produced by broken expr like '2.var + 2'
      if (parentContext.isBrokenStatement((PsiStatement) element)) return null;

      return (PsiStatement) element;
    }

    return null;
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

    // fix range from 'a > b.if' to 'a > b'
    // fix range from 'o instanceof T.if' to 'o instanceof T'
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