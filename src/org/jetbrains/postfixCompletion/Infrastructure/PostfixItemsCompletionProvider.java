package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.util.*;

public final class PostfixItemsCompletionProvider {
  public static void addCompletions(
    @NotNull CompletionParameters parameters, @NotNull CompletionResultSet resultSet,
    @NotNull PostfixExecutionContext executionContext) {

    Application application = ApplicationManager.getApplication();
    PostfixTemplatesManager manager = application.getComponent(PostfixTemplatesManager.class);

    PsiElement positionElement = parameters.getPosition();
    PostfixTemplateContext acceptanceContext = manager.isAvailable(positionElement, executionContext);

    if (acceptanceContext != null) {
      for (LookupElement lookupElement : manager.collectTemplates(acceptanceContext))
        resultSet.addElement(lookupElement);
    }
  }

  public static List<LookupElement> addCompletions2(
    @NotNull CompletionParameters parameters, @NotNull PostfixExecutionContext executionContext,
    final PsiReferenceExpression mockExpression) {

    Application application = ApplicationManager.getApplication();
    PostfixTemplatesManager manager = application.getComponent(PostfixTemplatesManager.class);

    PsiElement positionElement = parameters.getPosition();
    //PostfixTemplateContext acceptanceContext = manager.isAvailable(
    //  mockExpression.getReferenceNameElement(), executionContext);

    PsiElement reference = positionElement.getParent();

    PostfixTemplateContext acceptanceContext = new PostfixTemplateContext(
      reference, (PsiExpression) reference, executionContext) {


      @NotNull @Override protected List<PrefixExpressionContext> buildExpressionContexts(
        @NotNull PsiElement reference, @NotNull PsiExpression expression) {

        final PsiReferenceExpression qualifier = (PsiReferenceExpression) mockExpression.getQualifier();

        return Collections.<PrefixExpressionContext>singletonList(
          new PrefixExpressionContext(this, expression) {
            @Nullable @Override protected PsiType calculateExpressionType() {
              return qualifier.getType();
            }

            @Nullable @Override protected PsiElement calculateReferencedElement() {
              return qualifier.resolve();
            }

            @NotNull @Override protected TextRange calculateExpressionRange() {
              return super.calculateExpressionRange();
            }
          }
          // mock type, mock referenced element?
        );

      }

      @NotNull @Override public PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context) {
        return context;
      }

      @Override public boolean isBrokenStatement(@NotNull PsiStatement statement) {
        return super.isBrokenStatement(statement);
      }
    };

    if (acceptanceContext != null) {
      //acceptanceContext.outerExpression.setExpressionType(exprType);
      return manager.collectTemplates(acceptanceContext);
    }

    return Collections.emptyList();
  }
}