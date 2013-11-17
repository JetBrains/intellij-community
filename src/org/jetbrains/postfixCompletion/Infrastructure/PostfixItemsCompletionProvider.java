package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

public final class PostfixItemsCompletionProvider {
  public static void addCompletions(
      @NotNull CompletionParameters parameters, @NotNull CompletionResultSet resultSet,
      @NotNull PostfixExecutionContext executionContext) {
    Application application = ApplicationManager.getApplication();
    PostfixTemplatesManager templatesManager = application.getComponent(PostfixTemplatesManager.class);

    PsiElement positionElement = parameters.getPosition();
    PostfixTemplateContext context = templatesManager.isAvailable(positionElement, executionContext);

    if (context != null) {
      for (LookupElement postfixElement : templatesManager.collectTemplates(context))
        resultSet.addElement(postfixElement);
    }
  }
}