package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.util.*;

public abstract class PostfixTemplateAcceptanceContext {
  // can be 'PsiReferenceExpression' or 'PsiJavaCodeReferenceElement'
  @NotNull public final PsiElement postfixReference;
  @NotNull public final List<PrefixExpressionContext> expressions;
  public final boolean isForceMode;

  public PostfixTemplateAcceptanceContext(@NotNull final PsiElement reference,
                                          @NotNull final PsiExpression expression,
                                          boolean forceMode) {
    postfixReference = reference;
    isForceMode = forceMode;

    final List<PrefixExpressionContext> contexts = new ArrayList<PrefixExpressionContext>();
    final int referenceEndRange = reference.getTextRange().getEndOffset();

    // build expression contexts
    for (PsiElement node = expression; node != null; node = node.getParent()) {
      if (node instanceof PsiStatement) break;

      // handle only expressions, except 'reference'
      if (node instanceof PsiExpression && node != reference) {
        final PsiExpression expr = (PsiExpression) node;

        final int endOffset = expr.getTextRange().getEndOffset();
        if (endOffset > referenceEndRange) break; // stop when 'a.var + b'

        final PrefixExpressionContext context = new PrefixExpressionContext(this, expr);
        contexts.add(context);

        if (context.canBeStatement) break;
      }
    }

    expressions = Collections.unmodifiableList(contexts);
  }

  @NotNull public abstract PrefixExpressionContext fixUpExpression(
    @NotNull final PrefixExpressionContext context);
}