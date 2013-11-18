package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.util.*;

public abstract class PostfixTemplateContext {
  // can be 'PsiReferenceExpression' or 'PsiJavaCodeReferenceElement'

  // todo: use PsiJavaCodeReferenceElement everywhere
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
      if (node instanceof PsiExpression && node != reference) {
        PsiExpression expr = (PsiExpression) node;

        int endOffset = expr.getTextRange().getEndOffset();
        if (endOffset > referenceEndRange) break; // stop when 'a.var + b'

        PrefixExpressionContext context = new PrefixExpressionContext(this, expr);
        contexts.add(context);

        if (context.canBeStatement) break;
      }

      // todo: node instanceof PsiTypeElement?
    }

    return contexts;
  }

  @NotNull public abstract PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context);

  public boolean isBrokenStatement(@NotNull PsiStatement statement) { return false; }

  // todo: use me (when? in .new/.throw template?)
  // todo: drop me :O
  public boolean isFakeContextFromType() { return false; }
}