package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class PostfixTemplateAcceptanceContext {
  @NotNull public final PsiReferenceExpression referenceExpression;
  @NotNull public final List<PrefixExpressionContext> expressions;
  public final boolean isForceMode;

  public PostfixTemplateAcceptanceContext(
    @NotNull final PsiReferenceExpression reference,
    @NotNull final PsiExpression expression,
    boolean forceMode) {

    referenceExpression = reference;
    isForceMode = forceMode;

    final ArrayList<PrefixExpressionContext> contexts = new ArrayList<>();
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