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
  @Override
  public void createItems(@NotNull final PostfixTemplateAcceptanceContext context,
                          @NotNull final List<LookupElement> consumer) {

    for (PrefixExpressionContext expression : context.expressions) {
      if (expression.canBeStatement) {
        consumer.add(new Foo(expression));
        break;
      }
    }

  }

  static class Foo extends StatementPostfixLookupElement<PsiExpressionStatement> {

    public Foo(@NotNull final PrefixExpressionContext context) {
      super("var", context);
    }



    @NotNull @Override
    protected PsiExpressionStatement createNewStatement(
      @NotNull PsiElementFactory factory,
      @NotNull PsiExpression expression,
      @NotNull PsiFile context) {




      final PsiExpressionStatement expr = (PsiExpressionStatement)
        factory.createStatementFromText("expr", context);

      expr.getExpression().replace(expression);
      //IntroduceVariableAction

      return expr;
    }

    @Override public void handleInsert(final InsertionContext context) {
      // MMM?
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override public void run() {
          Foo.super.handleInsert(context);
        }
      });
    }

    @Override
    protected void postProcess(InsertionContext context, PsiExpressionStatement statement) {




      final ActionManager manager = ActionManager.getInstance();
      final AnAction introduceParameter =  manager.getAction("IntroduceVariable");
      final InputEvent event = ActionCommand.getInputEvent("IntroduceVariable");

      ActionManager.getInstance().tryToExecute(introduceParameter, event,
        null, ActionPlaces.UNKNOWN, true);

      //manager.tryToExecute()

      //IntroduceVariableAction.
    }
  }
}
