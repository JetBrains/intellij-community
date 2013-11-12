package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.util.*;

public abstract class PostfixTemplateContext {
  // can be 'PsiReferenceExpression' or 'PsiJavaCodeReferenceElement'
  @NotNull public final PsiElement postfixReference;
  @NotNull public final List<PrefixExpressionContext> expressions;
  @NotNull public final PrefixExpressionContext outerExpression, innerExpression;
  @NotNull public final PostfixExecutionContext executionContext;

  public PostfixTemplateContext(
    @NotNull PsiElement reference, @NotNull PsiExpression expression,
    @NotNull PostfixExecutionContext executionContext) {
    postfixReference = reference;
    this.executionContext = executionContext;

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
    }

    expressions = Collections.unmodifiableList(contexts);
    innerExpression = contexts.get(0);
    outerExpression = contexts.get(contexts.size() - 1);
  }

  @NotNull public abstract PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context);

  public boolean isBrokenStatement(@NotNull PsiStatement statement) { return false; }
  // todo: use me (when? in .new template?)
  public boolean isFakeContextFromType() { return false; }
}