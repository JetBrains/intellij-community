package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElementBase;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;
import org.jetbrains.postfixCompletion.util.CommonUtils;

import static org.jetbrains.postfixCompletion.util.CommonUtils.CtorAccessibility;

// todo: support for int[].var (parses as .class access!)

@TemplateInfo(
  templateName = "var",
  description = "Introduces variable for expression",
  example = "T name = expr;",
  worksOnTypes = true)
public final class IntroduceVariablePostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext forcedTarget = null;
    PsiClass invokedOnType = null;

    for (PrefixExpressionContext expressionContext : context.expressions()) {
      PsiElement expression = expressionContext.expression;
      // filter out from 'this' and 'super'
      if (expression instanceof PsiThisExpression ||
          expression instanceof PsiSuperExpression) {
        forcedTarget = expressionContext;
        continue;
      }

      PsiElement referenced = expressionContext.referencedElement;
      if (referenced != null) {
        if (referenced instanceof PsiClass) {
          invokedOnType = (PsiClass)referenced;

          // if enum/class without constructors accessible in current context
          CtorAccessibility accessibility = CommonUtils.isTypeCanBeInstantiatedWithNew(invokedOnType, expression);
          if (accessibility == CtorAccessibility.NotAccessible) break;
        }
        else {
          // and 'too simple' expressions (except force mode)
          if (referenced instanceof PsiLocalVariable ||
              referenced instanceof PsiParameter ||
              referenced instanceof LightElement) {
            forcedTarget = expressionContext;
            continue;
          }
        }
      }

      // disable this provider when expression type is unknown
      PsiType expressionType = expressionContext.expressionType;
      if (expressionType == null && invokedOnType == null) {
        // for simple expressions like `expr.postfix`
        if (context.expressions().size() == 1) break;
      }

      // disable on expressions (invocations) of void type
      if (expressionType != null && expressionType.equals(PsiType.VOID)) continue;

      if (expressionContext.canBeStatement) {
        return new IntroduceVarStatementLookupElement(expressionContext, invokedOnType);
        // avoid multiple .var templates
      }
      else {
        forcedTarget = expressionContext;
      }
    }

    // force mode - enable inside expressions/for locals/parameters/etc
    if (forcedTarget != null && context.executionContext.isForceMode) {
      if (forcedTarget.referencedElement instanceof PsiClass) {
        invokedOnType = (PsiClass)forcedTarget.referencedElement;
      }

      if (forcedTarget.canBeStatement) {
        return new IntroduceVarStatementLookupElement(forcedTarget, invokedOnType);
      }
      else {
        return new IntroduceVarExpressionLookupElement(forcedTarget, invokedOnType);
      }
    }

    return null;
  }

  private static class IntroduceVarStatementLookupElement extends StatementPostfixLookupElement<PsiExpressionStatement> {
    private final boolean myInvokedOnType;
    private final boolean myIsAbstractType;

    public IntroduceVarStatementLookupElement(@NotNull PrefixExpressionContext context, @Nullable PsiClass invokedOnType) {
      super("var", context);
      myInvokedOnType = (invokedOnType != null);
      myIsAbstractType = myInvokedOnType && CommonUtils.isTypeRequiresRefinement(invokedOnType);
    }

    @NotNull
    @Override
    protected PsiExpressionStatement createNewStatement(@NotNull PsiElementFactory factory,
                                                        @NotNull PsiElement expression,
                                                        @NotNull PsiElement context) {
      if (myInvokedOnType) {
        String template = "new " + expression.getText() + "()";
        if (myIsAbstractType) template += "{}";
        expression = factory.createExpressionFromText(template, context);
      }

      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)factory.createStatementFromText("expr", context);

      expressionStatement.getExpression().replace(expression);
      return expressionStatement;
    }

    @Override
    public void handleInsert(@NotNull final InsertionContext context) {
      // execute insertion without undo manager enabled
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          IntroduceVarStatementLookupElement.super.handleInsert(context);
        }
      });
    }

    @Override
    protected void postProcess(@NotNull final InsertionContext context, @NotNull final PsiExpressionStatement statement) {
      context.getEditor().getCaretModel().moveToOffset(statement.getExpression().getTextRange().getEndOffset());

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        context.setLaterRunnable(new Runnable() {
          @Override
          public void run() {
            IntroduceVariableHandler handler = getMockHandler();
            handler.invoke(context.getProject(), context.getEditor(), statement.getExpression());
          }
        });
      }
      else {
        IntroduceVariableHandler handler = new IntroduceVariableHandler();
        handler.invoke(context.getProject(), context.getEditor(), statement.getExpression());
      }

      // todo: somehow handle success introduce variable and place caret in a smart way
    }
  }

  private static class IntroduceVarExpressionLookupElement extends ExpressionPostfixLookupElementBase<PsiExpression> {
    private final boolean myInvokedOnType;
    private final boolean myIsAbstractType;

    public IntroduceVarExpressionLookupElement(@NotNull PrefixExpressionContext context, @Nullable PsiClass invokedOnType) {
      super("var", context);
      myInvokedOnType = (invokedOnType != null);
      myIsAbstractType = myInvokedOnType &&
                         (invokedOnType.isInterface() || invokedOnType.hasModifierProperty(PsiModifier.ABSTRACT));
    }

    @NotNull
    @Override
    protected PsiExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      if (myInvokedOnType) {
        String template = "new " + expression.getText() + "()";
        if (myIsAbstractType) template += "{}";
        expression = factory.createExpressionFromText(template, context);
      }

      return (PsiExpression)expression;
    }

    @Override
    public void handleInsert(@NotNull final InsertionContext context) {
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
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
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
      });
    }

    @Override
    protected void postProcess(@NotNull InsertionContext context, @NotNull PsiExpression expression) {
      context.getEditor().getCaretModel().moveToOffset(expression.getTextRange().getEndOffset());

      boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      IntroduceVariableHandler handler = unitTestMode ? getMockHandler() : new IntroduceVariableHandler();

      //String text = context.getDocument().getText();
      handler.invoke(context.getProject(), context.getEditor(), expression);
    }
  }

  @NotNull
  private static IntroduceVariableHandler getMockHandler() {
    return new IntroduceVariableHandler() {
      // mock default settings
      @Override
      public final IntroduceVariableSettings getSettings(
        Project project, Editor editor, final PsiExpression expr, PsiExpression[] occurrences,
        TypeSelectorManagerImpl typeSelectorManager, boolean declareFinalIfAll, boolean anyAssignmentLHS,
        InputValidator validator, PsiElement anchor, OccurrencesChooser.ReplaceChoice replaceChoice) {
        return new IntroduceVariableSettings() {
          @Override
          public String getEnteredName() {
            return "foo";
          }

          @Override
          public boolean isReplaceAllOccurrences() {
            return false;
          }

          @Override
          public boolean isDeclareFinal() {
            return false;
          }

          @Override
          public boolean isReplaceLValues() {
            return false;
          }

          @Override
          public PsiType getSelectedType() {
            return expr.getType();
          }

          @Override
          public boolean isOK() {
            return true;
          }
        };
      }
    };
  }
}