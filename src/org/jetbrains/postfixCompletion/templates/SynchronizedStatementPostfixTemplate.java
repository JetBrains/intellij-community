package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;

@TemplateInfo(
  templateName = "synchronized",
  description = "Produces synchronization statement",
  example = "synchronized (expr)")
public class SynchronizedStatementPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext expression = context.outerExpression();
    if (!expression.canBeStatement) return null;

    PsiType expressionType = expression.expressionType;
    if (expressionType instanceof PsiPrimitiveType) return null;

    if (!context.executionContext.isForceMode) {
      if (expressionType == null) return null;
      if (!expressionType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return null;
    }

    return new SynchronizedLookupElement(expression);
  }
  
  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    throw new UnsupportedOperationException("Implement me please");
  }

  static final class SynchronizedLookupElement extends StatementPostfixLookupElement<PsiSynchronizedStatement> {
    public SynchronizedLookupElement(@NotNull PrefixExpressionContext context) {
      super("synchronized", context);
    }

    @NotNull
    @Override
    protected PsiSynchronizedStatement createNewStatement(@NotNull PsiElementFactory factory,
                                                          @NotNull PsiElement expression,
                                                          @NotNull PsiElement context) {
      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)factory.createStatementFromText("synchronized (expr)", context);
      PsiExpression lockExpression = synchronizedStatement.getLockExpression();
      assert lockExpression != null;
      lockExpression.replace(expression);
      return synchronizedStatement;
    }

    @Override
    protected void postProcess(@NotNull final InsertionContext context, @NotNull PsiSynchronizedStatement statement) {
      // look for right parenthesis
      for (PsiElement node = statement.getLockExpression(); node != null; node = node.getNextSibling()) {
        if (node instanceof PsiJavaToken && ((PsiJavaToken)node).getTokenType() == JavaTokenType.RPARENTH) {
          int offset = node.getTextRange().getEndOffset();
          context.getEditor().getCaretModel().moveToOffset(offset);
          return;
        }
      }
    }
  }
}