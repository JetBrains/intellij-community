package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.*;
import com.intellij.openapi.ui.playback.commands.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.awt.event.*;
import java.util.*;

@TemplateProvider(
  templateName = "var",
  description = "Introduces variable for expression",
  example = "var x = expr;")
public class IntroduceVariableTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull PostfixTemplateAcceptanceContext context, @NotNull List<LookupElement> consumer) {

    // todo: support expressions
    // todo: setup selection before refactoring? or context?
    // todo: disable when qualifier type is unknown (what about broken, but fixable exprs?)

    for (PrefixExpressionContext expression : context.expressions)
      if (expression.canBeStatement) {
        consumer.add(new IntroduceVarLookupElement(expression));
        break;
      }
  }

  private static class IntroduceVarLookupElement extends StatementPostfixLookupElement<PsiExpressionStatement> {
    public IntroduceVarLookupElement(@NotNull PrefixExpressionContext context) {
      super("var", context);
    }

    @NotNull @Override protected PsiExpressionStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiFile context) {

      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement) factory.createStatementFromText("expr", context);

      expressionStatement.getExpression().replace(expression);
      return expressionStatement;
    }

    @Override public void handleInsert(@NotNull final InsertionContext context) {
      // execute insertion without undo manager enabled
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override public void run() {
          IntroduceVarLookupElement.super.handleInsert(context);
        }
      });
    }

    public static final String INTRODUCE_VARIABLE = "IntroduceVariable";

    @Override protected void postProcess(
      @NotNull InsertionContext context, @NotNull PsiExpressionStatement statement) {
      ActionManager manager = ActionManager.getInstance();
      AnAction introduceVariable =  manager.getAction(INTRODUCE_VARIABLE);
      InputEvent event = ActionCommand.getInputEvent(INTRODUCE_VARIABLE);

      ActionManager.getInstance().tryToExecute(
        introduceVariable, event, null, ActionPlaces.UNKNOWN, true);
    }
  }
}
