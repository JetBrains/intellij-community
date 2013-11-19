package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.util.*;

public abstract class PostfixTemplateContext {
  @NotNull public final PsiJavaCodeReferenceElement postfixReference;
  @NotNull public final List<PrefixExpressionContext> expressions;
  @NotNull public final PrefixExpressionContext outerExpression, innerExpression;
  @NotNull public final PostfixExecutionContext executionContext;
  public final boolean insideCodeFragment;

  public PostfixTemplateContext(
    @NotNull PsiJavaCodeReferenceElement reference, @NotNull PsiElement expression,
    @NotNull PostfixExecutionContext executionContext) {
    postfixReference = reference;
    this.executionContext = executionContext;
    insideCodeFragment = (reference.getContainingFile() instanceof PsiCodeFragment);

    List<PrefixExpressionContext> contexts = buildExpressionContexts(reference, expression);

    expressions = Collections.unmodifiableList(contexts);
    innerExpression = contexts.get(0);
    outerExpression = contexts.get(contexts.size() - 1);
  }

  @NotNull protected List<PrefixExpressionContext> buildExpressionContexts(
      @NotNull PsiElement reference, @NotNull PsiElement expression) {
    List<PrefixExpressionContext> contexts = new ArrayList<PrefixExpressionContext>();
    int referenceEndRange = reference.getTextRange().getEndOffset();

    // build expression contexts
    for (PsiElement node = expression; node != null; node = node.getParent()) {
      if (node instanceof PsiStatement) break;

      // handle only expressions, except 'reference'
      if ((node instanceof PsiExpression ||
           node instanceof PsiJavaCodeReferenceElement) && node != reference) {
        int endOffset = node.getTextRange().getEndOffset();
        if (endOffset > referenceEndRange) break; // stop when 'a.var + b'

        PrefixExpressionContext context = new PrefixExpressionContext(this, node);
        contexts.add(context);

        if (context.canBeStatement) break;
      }
    }

    return contexts;
  }

  @NotNull public abstract PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context);

  @Nullable public PsiStatement getContainingStatement(@NotNull PrefixExpressionContext expressionContext) {
    // look for expression-statement parent
    PsiElement element = expressionContext.expression.getParent();

    // escape from '.postfix' reference-expression
    if (element == postfixReference) {
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
      return (PsiStatement) element;
    }

    return null;
  }

  // todo: getContainingExpression?
}