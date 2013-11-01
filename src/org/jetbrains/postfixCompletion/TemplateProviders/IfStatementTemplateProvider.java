package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.LookupItems.PostfixLookupItem;

import java.util.List;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public class IfStatementTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull final PostfixTemplateAcceptanceContext context,
    @NotNull final List<LookupElement> consumer) {

    // todo: handle Boolean?
    // todo: handle force mode
    // todo: handle unknown type?

    for (final PrefixExpressionContext expressionContext : context.getExpressions()) {
      final PsiType expressionType = expressionContext.expressionType;
      if (expressionType != null) {
        if (expressionType == PsiType.BOOLEAN) {
          final IfLookupElement lookupElement = new IfLookupElement(expressionContext);
          consumer.add(lookupElement);
          break;
        }
      }
    }
  }

  private static final class IfLookupElement extends PostfixLookupItem {


    //private final PsiExpression myFoo;

    public IfLookupElement(@NotNull final PrefixExpressionContext context) {
      super("if");

      //myFoo = context.expression;

      //context.expression
    }

    @Override
    public void handleInsert(InsertionContext context) {

      //ApplicationManager.getApplication()


      super.handleInsert(context);


    }
  }
}

