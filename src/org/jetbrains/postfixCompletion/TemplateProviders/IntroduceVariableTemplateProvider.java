package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.refactoring.introduce.inplace.*;
import com.intellij.refactoring.introduceVariable.*;
import com.intellij.refactoring.ui.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "var",
  description = "Introduces variable for expression",
  example = "var x = expr;")
public class IntroduceVariableTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {

    // todo: support on expressions

    PrefixExpressionContext forcedTarget = null;
    PsiClass invokedOnType = null;

    for (PrefixExpressionContext expressionContext : context.expressions) {
      PsiExpression expression = expressionContext.expression;
      PsiElement referenced = expressionContext.referencedElement;

      if (referenced != null) {
        if (referenced instanceof PsiClass) {
          invokedOnType = (PsiClass) referenced;
          // enumerations can't be instantiated
          if (invokedOnType.isEnum()) break;
        } else {
          // filter out packages
          if (referenced instanceof PsiPackage) continue;
          // and 'too simple' expressions (except force mode)
          if (referenced instanceof PsiLocalVariable || referenced instanceof PsiParameter) {
            forcedTarget = expressionContext;
            continue;
          }
        }
      }

      // disable this provider when expression type is unknown
      PsiType expressionType = expression.getType();
      if (expressionType == null && invokedOnType == null) {
        // for simple expressions like `expr.postfix`
        if (context.expressions.size() == 1) break;
      }

      // disable on expressions (invocations) of void type
      if (expressionType != null && expressionType.equals(PsiType.VOID)) continue;

      if (expressionContext.canBeStatement) {
        consumer.add(new IntroduceVarStatementLookupElement(expressionContext, invokedOnType));
        return; // avoid multiple .var templates
      } else {
        forcedTarget = expressionContext;
      }
    }

    // force mode - enable inside expressions/for locals/parameters/etc
    if (forcedTarget != null && context.executionContext.isForceMode) {
      if (forcedTarget.referencedElement instanceof PsiClass) {
        invokedOnType = (PsiClass) forcedTarget.referencedElement;
      }

      if (forcedTarget.canBeStatement) {
        consumer.add(new IntroduceVarStatementLookupElement(forcedTarget, invokedOnType));
      } else {
        consumer.add(new IntroduceVarExpressionLookupElement(forcedTarget, invokedOnType));
      }
    }
  }

  private static class IntroduceVarStatementLookupElement
    extends StatementPostfixLookupElement<PsiExpressionStatement> {
    private final boolean myInvokedOnType, myIsAbstractType;

    public IntroduceVarStatementLookupElement(
      @NotNull PrefixExpressionContext context, @Nullable PsiClass invokedOnType) {
      super("var", context);
      myInvokedOnType = (invokedOnType != null);
      myIsAbstractType = myInvokedOnType &&
        (invokedOnType.isInterface() || invokedOnType.hasModifierProperty(PsiModifier.ABSTRACT));
    }

    @NotNull @Override protected PsiExpressionStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {

      if (myInvokedOnType) {
        String template = "new " + expression.getText() + "()";
        if (myIsAbstractType) template += "{}";
        expression = factory.createExpressionFromText(template, context);
      }

      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement) factory.createStatementFromText("expr", context);

      expressionStatement.getExpression().replace(expression);
      return expressionStatement;
    }

    @Override public void handleInsert(@NotNull final InsertionContext context) {
      // execute insertion without undo manager enabled
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override public void run() {
          IntroduceVarStatementLookupElement.super.handleInsert(context);
        }
      });
    }

    @Override protected void postProcess(
      @NotNull final InsertionContext context, @NotNull final PsiExpressionStatement statement) {

      context.setLaterRunnable(new Runnable() {
        @Override public void run() {
          boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
          IntroduceVariableHandler handler = unitTestMode ? getMockHandler() : new IntroduceVariableHandler();

          handler.invoke(context.getProject(), context.getEditor(), statement.getExpression());
          // todo: somehow handle success introduce variable and place caret in a smart way
        }
      });


    }
  }

  private static class IntroduceVarExpressionLookupElement
    extends ExpressionPostfixLookupElement<PsiExpression> {
    private final boolean myInvokedOnType, myIsAbstractType;

    public IntroduceVarExpressionLookupElement(
      @NotNull PrefixExpressionContext context, @Nullable PsiClass invokedOnType) {
      super("var", context);
      myInvokedOnType = (invokedOnType != null);
      myIsAbstractType = myInvokedOnType &&
        (invokedOnType.isInterface() || invokedOnType.hasModifierProperty(PsiModifier.ABSTRACT));
    }

    @NotNull @Override protected PsiExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {
      if (myInvokedOnType) {
        String template = "new " + expression.getText() + "()";
        if (myIsAbstractType) template += "{}";
        expression = factory.createExpressionFromText(template, context);
      }

      return expression;
    }

    @Override public void handleInsert(@NotNull final InsertionContext context) {
      final Runnable runnable = new Runnable() {
        @Override public void run() {
          IntroduceVarExpressionLookupElement.super.handleInsert(context);
        }
      };

      // normally - execute immediately
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        CommandProcessor.getInstance().runUndoTransparentAction(runnable);
        return;
      }

      // execute postponed to workaround watching completion context
      context.setLaterRunnable(new Runnable() {
        @Override public void run() {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
      });
    }

    @Override protected void postProcess(
      @NotNull InsertionContext context, @NotNull PsiExpression expression) {
      boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      IntroduceVariableHandler handler = unitTestMode ? getMockHandler() : new IntroduceVariableHandler();

      //String text = context.getDocument().getText();
      handler.invoke(context.getProject(), context.getEditor(), expression);
    }
  }

  @NotNull private static IntroduceVariableHandler getMockHandler() {
    return new IntroduceVariableHandler() {
      // mock default settings
      @Override public final IntroduceVariableSettings getSettings(
        Project project, Editor editor, final PsiExpression expr, PsiExpression[] occurrences,
        TypeSelectorManagerImpl typeSelectorManager, boolean declareFinalIfAll, boolean anyAssignmentLHS,
        InputValidator validator, PsiElement anchor, OccurrencesChooser.ReplaceChoice replaceChoice) {
        return new IntroduceVariableSettings() {
          @Override public String getEnteredName() { return "foo"; }
          @Override public boolean isReplaceAllOccurrences() { return false; }
          @Override public boolean isDeclareFinal() { return false; }
          @Override public boolean isReplaceLValues() { return false; }
          @Override public PsiType getSelectedType() { return expr.getType(); }
          @Override public boolean isOK() { return true; }
        };
      }
    };
  }
}