package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;

@TemplateInfo(
  templateName = "return",
  description = "Returns value from containing method",
  example = "return expr;")
public final class ReturnStatementPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext expression = context.outerExpression();
    if (!expression.canBeStatement) return null;

    PsiElement node = expression.expression;
    PsiMethod method = null;

    // check we are inside method
    do {
      // (stop on anonymous/local classes)
      if (node instanceof PsiClass) return null;

      if (node instanceof PsiMethod) {
        method = (PsiMethod)node;
        break;
      }

      node = node.getParent();
    }
    while (node != null);

    if (method == null) return null; // :(

    if (!context.executionContext.isForceMode) {
      PsiType returnType = method.getReturnType();
      if (returnType == null || returnType.equals(PsiType.VOID)) return null;

      // check expression type if type is known
      PsiType expressionType = expression.expressionType;
      if (expressionType != null && !returnType.isAssignableFrom(expressionType)) return null;
    }

    return new ReturnLookupElement(expression);
  }
  
  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    throw new UnsupportedOperationException("Implement me please");
  }

  static final class ReturnLookupElement extends StatementPostfixLookupElement<PsiReturnStatement> {
    public ReturnLookupElement(@NotNull PrefixExpressionContext expression) {
      super("return", expression);
    }

    @NotNull
    @Override
    protected PsiReturnStatement createNewStatement(@NotNull PsiElementFactory factory,
                                                    @NotNull PsiElement expression,
                                                    @NotNull PsiElement context) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)factory.createStatementFromText("return expr;", expression);
      PsiExpression returnValue = returnStatement.getReturnValue();
      assert returnValue != null;
      returnValue.replace(expression);
      return returnStatement;
    }
  }
}
