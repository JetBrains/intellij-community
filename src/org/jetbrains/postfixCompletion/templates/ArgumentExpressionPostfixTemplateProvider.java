package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElementBase;
import org.jetbrains.postfixCompletion.util.CommonUtils;

import java.util.List;

@TemplateProvider(
  templateName = "arg",
  description = "Surrounds expression with invocation",
  example = "someMethod(expr)",
  worksInsideFragments = true)
public final class ArgumentExpressionPostfixTemplateProvider extends PostfixTemplateProvider {
  @Override
  public void createItems(@NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression();
    if (context.executionContext.isForceMode) {
      consumer.add(new ArgumentLookupElement(expression));
    }
    else if (expression.canBeStatement) {
      if (expression.expressionType == null) return; // do not show over unresolved symbols
      if (expression.referencedElement instanceof PsiClass) return; // do not show over types
      if (!CommonUtils.isNiceExpression(expression.expression)) return; // void expressions and etc.

      // foo.bar().baz.arg
      consumer.add(new ArgumentLookupElement(expression));
    }
  }

  private static class ArgumentLookupElement extends ExpressionPostfixLookupElementBase<PsiMethodCallExpression> {
    public ArgumentLookupElement(@NotNull PrefixExpressionContext context) {
      super("arg", context);
    }

    @NotNull
    @Override
    protected PsiMethodCallExpression createNewExpression(@NotNull PsiElementFactory factory,
                                                          @NotNull PsiElement expression,
                                                          @NotNull PsiElement context) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)factory.createExpressionFromText("method(expr)", context);

      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      argumentList.getExpressions()[0].replace(expression);

      return methodCallExpression;
    }

    @Override
    protected void postProcess(@NotNull final InsertionContext context, @NotNull PsiMethodCallExpression expression) {
      SmartPointerManager pointerManager = SmartPointerManager.getInstance(context.getProject());
      final SmartPsiElementPointer<PsiMethodCallExpression> pointer = pointerManager.createSmartPsiElementPointer(expression);

      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          PsiMethodCallExpression callExpression = pointer.getElement();
          if (callExpression == null) return;

          TemplateBuilderImpl builder = new TemplateBuilderImpl(callExpression);

          builder.replaceElement(
            callExpression.getMethodExpression(), new TextExpression("method"));

          PsiExpressionList argumentList = callExpression.getArgumentList();
          builder.setEndVariableAfter(argumentList.getExpressions()[0]);

          Template template = builder.buildInlineTemplate();

          Editor editor = context.getEditor();
          editor.getCaretModel().moveToOffset(
            callExpression.getTextRange().getStartOffset());

          TemplateManager manager = TemplateManager.getInstance(context.getProject());
          manager.startTemplate(editor, template);
        }
      };

      context.setLaterRunnable(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              CommandProcessor.getInstance().runUndoTransparentAction(runnable);
            }
          });
        }
      });
    }
  }
}