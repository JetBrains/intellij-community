package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "arg",
  description = "Surrounds expression with invocation",
  example = "someMethod(expr)", worksOnTypes = true)
public class ArgumentExpressionTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression;
    if (context.executionContext.isForceMode) {
      consumer.add(new ArgumentLookupElement(expression));
    } else if (expression.canBeStatement) {
      if (!CommonUtils.isNiceExpression(expression.expression)) return;
      // foo.bar().baz.arg
      consumer.add(new ArgumentLookupElement(expression));
    }
  }

  private static class ArgumentLookupElement
    extends ExpressionPostfixLookupElement<PsiMethodCallExpression> {

    public ArgumentLookupElement(@NotNull PrefixExpressionContext context) {
      super("arg", context);
    }

    @NotNull @Override protected PsiMethodCallExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {

      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)
        factory.createExpressionFromText("method(expr)", context);

      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      argumentList.getExpressions()[0].replace(expression);

      return methodCallExpression;
    }

    @Override protected void postProcess(
      @NotNull final InsertionContext context, @NotNull PsiMethodCallExpression expression) {

      SmartPointerManager pointerManager = SmartPointerManager.getInstance(context.getProject());
      final SmartPsiElementPointer<PsiMethodCallExpression> pointer =
        pointerManager.createSmartPsiElementPointer(expression);

      context.setLaterRunnable(new Runnable() {
        @Override public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override public void run() {
              PsiMethodCallExpression callExpression = pointer.getElement();
              if (callExpression == null) return;

              TemplateBuilderImpl builder = new TemplateBuilderImpl(callExpression);

              builder.replaceElement(
                callExpression.getMethodExpression(), new MacroCallNode(new CompleteMacro()));

              PsiExpressionList argumentList = callExpression.getArgumentList();
              builder.setEndVariableAfter(argumentList.getExpressions()[0]);

              Template template = builder.buildInlineTemplate();

              Editor editor = context.getEditor();
              editor.getCaretModel().moveToOffset(
                callExpression.getTextRange().getStartOffset());

              TemplateManager manager = TemplateManager.getInstance(context.getProject());
              manager.startTemplate(editor, template);
            }
          });
        }
      });
    }
  }
}