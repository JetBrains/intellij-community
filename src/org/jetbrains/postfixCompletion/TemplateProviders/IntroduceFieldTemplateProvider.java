package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.refactoring.introduceField.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "field",
  description = "Introduces field for expression",
  example = "_field = expr;")
public class IntroduceFieldTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {

    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(context.postfixReference, PsiMethod.class);
    if (containingMethod == null) return;

    if (context.executionContext.isForceMode || containingMethod.isConstructor()) {
      for (PrefixExpressionContext expressionContext : context.expressions()) {
        if (expressionContext.expressionType == null) continue;
        if (!expressionContext.canBeStatement) continue;

        PsiElement expression = expressionContext.expression;

        // filter out from 'this' and 'super'
        if (expression instanceof PsiThisExpression ||
            expression instanceof PsiSuperExpression) continue;

        // filter out non-qualified references to other fields (even for outer classes fields)
        if (expression instanceof PsiReferenceExpression &&
            ((PsiReferenceExpression) expression).getQualifier() == null) {

          if (expressionContext.referencedElement instanceof PsiField &&
              !context.executionContext.isForceMode) continue;
        }

        consumer.add(new IntroduceFieldLookupElement(expressionContext));
      }
    }
  }

  private static class IntroduceFieldLookupElement extends StatementPostfixLookupElement<PsiExpressionStatement> {
    public IntroduceFieldLookupElement(@NotNull PrefixExpressionContext context) {
      super("field", context);
    }

    @NotNull @Override protected PsiExpressionStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement) factory.createStatementFromText("expr", context);

      expressionStatement.getExpression().replace(expression);
      return expressionStatement;
    }

    @Override public void handleInsert(@NotNull final InsertionContext context) {
      // execute insertion without undo manager enabled
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override public void run() {
          IntroduceFieldLookupElement.super.handleInsert(context);
        }
      });
    }

    @Override protected void postProcess(
      @NotNull final InsertionContext context, @NotNull final PsiExpressionStatement statement) {

      context.getEditor().getCaretModel().moveToOffset(
        statement.getExpression().getTextRange().getEndOffset());

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        context.setLaterRunnable(new Runnable() {
          @Override public void run() {
            IntroduceFieldHandler handler = getMockHandler(statement.getExpression());
            handler.invoke(context.getProject(), new PsiElement[] { statement.getExpression() }, null);
          }
        });
      } else {
        IntroduceFieldHandler handler = new IntroduceFieldHandler();
        handler.invoke(context.getProject(), new PsiElement[] { statement.getExpression() }, null);
      }
    }
  }

  @NotNull private static IntroduceFieldHandler getMockHandler(@NotNull final PsiExpression expression) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    assert (containingClass != null) : "containingClass != null";

    return new IntroduceFieldHandler() {
      // mock default settings
      @Override protected Settings showRefactoringDialog(
        Project project, Editor editor, PsiClass parentClass, PsiExpression expr, PsiType type,
        PsiExpression[] occurrences, PsiElement anchorElement, PsiElement anchorElementIfAll) {

        return new Settings(
          "foo", expression, PsiExpression.EMPTY_ARRAY, false, false, false,
          InitializationPlace.IN_CURRENT_METHOD, PsiModifier.PRIVATE, null,
          null, false, containingClass, false, false);
      }
    };
  }
}