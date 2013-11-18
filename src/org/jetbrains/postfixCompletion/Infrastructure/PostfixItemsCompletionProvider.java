package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

import java.util.*;

public abstract class PostfixItemsCompletionProvider {
  @NotNull public static List<LookupElement> getItems(
      @NotNull CompletionParameters parameters, @NotNull PostfixExecutionContext executionContext) {
    Application application = ApplicationManager.getApplication();
    PostfixTemplatesManager templatesManager = application.getComponent(PostfixTemplatesManager.class);

    PsiElement position = parameters.getPosition();

    PostfixTemplateContext context = templatesManager.isAvailable(position, executionContext);
    if (context == null) return Collections.emptyList();

    return templatesManager.collectTemplates(context);
  }
}