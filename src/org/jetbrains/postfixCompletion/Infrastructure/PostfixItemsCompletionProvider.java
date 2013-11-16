package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
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
    @NotNull CompletionParameters parameters, @NotNull PostfixExecutionContext executionContext, PsiType exprType) {

    Application application = ApplicationManager.getApplication();
    PostfixTemplatesManager manager = application.getComponent(PostfixTemplatesManager.class);

    PsiElement positionElement = parameters.getPosition();
    PostfixTemplateContext acceptanceContext = manager.isAvailable(positionElement, executionContext);


    if (acceptanceContext != null) {
      acceptanceContext.outerExpression.expressionType = exprType;
      return manager.collectTemplates(acceptanceContext);
    }

    return Collections.emptyList();
  }
}