package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "synchronized",
  description = "Produces synchronization statement",
  example = "synchronized (expr)")
public final class SynchronizedStatementTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression();
    if (!expression.canBeStatement) return;

    PsiType expressionType = expression.expressionType;
    if (expressionType instanceof PsiPrimitiveType) return;

    if (!context.executionContext.isForceMode) {
      if (expressionType == null) return;
      if (!expressionType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return;
    }

    consumer.add(new SynchronizedLookupElement(expression));
  }

  static final class SynchronizedLookupElement extends StatementPostfixLookupElement<PsiSynchronizedStatement> {
    public SynchronizedLookupElement(@NotNull PrefixExpressionContext context) {
      super("synchronized", context);
    }

    @NotNull @Override protected PsiSynchronizedStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {

      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement) factory.createStatementFromText("synchronized (expr)", context);

      PsiExpression lockExpression = synchronizedStatement.getLockExpression();
      assert (lockExpression != null) : "lockExpression != null";
      lockExpression.replace(expression);

      return synchronizedStatement;
    }

    @Override protected void postProcess(
        @NotNull final InsertionContext context, @NotNull PsiSynchronizedStatement statement) {
      // look for right parenthesis
      for (PsiElement node = statement.getLockExpression(); node != null; node = node.getNextSibling()) {
        if (node instanceof PsiJavaToken && ((PsiJavaToken) node).getTokenType() == JavaTokenType.RPARENTH) {
          int offset = node.getTextRange().getEndOffset();
          context.getEditor().getCaretModel().moveToOffset(offset);
          return;
        }
      }
    }
  }
}