package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "return",
  description = "Returns expression/yields value from iterator",
  example = "return expr;")
public final class ReturnStatementTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(@NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression();
    if (!expression.canBeStatement) return;

    PsiElement node = expression.expression;
    PsiMethod method = null;

    // check we are inside method
    do {
      // (stop on anonymous/local classes)
      if (node instanceof PsiClass) return;

      if (node instanceof PsiMethod) {
        method = (PsiMethod) node;
        break;
      }

      node = node.getParent();
    } while (node != null);

    if (method == null) return; // :(

    if (context.executionContext.isForceMode) {
      consumer.add(new ReturnLookupElement(expression));
    } else {
      PsiType returnType = method.getReturnType();
      if (returnType == null || returnType.equals(PsiType.VOID)) return;

      // check expression type if type is known
      PsiType expressionType = expression.expressionType;
      if (expressionType != null && !returnType.isAssignableFrom(expressionType)) return;

      consumer.add(new ReturnLookupElement(expression));
    }
  }

  static final class ReturnLookupElement extends StatementPostfixLookupElement<PsiReturnStatement> {
    public ReturnLookupElement(@NotNull PrefixExpressionContext expression) {
      super("return", expression);
    }

    @NotNull @Override protected PsiReturnStatement createNewStatement(
        @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      PsiReturnStatement returnStatement =
        (PsiReturnStatement) factory.createStatementFromText("return expr;", expression);

      PsiExpression returnValue = returnStatement.getReturnValue();
      assert (returnValue != null) : "returnValue != null";
      returnValue.replace(expression);

      return returnStatement;
    }
  }
}
