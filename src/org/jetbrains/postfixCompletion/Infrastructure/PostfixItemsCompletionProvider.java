package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.psi.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

public final class PostfixItemsCompletionProvider
  extends CompletionProvider<CompletionParameters> {

  @NotNull public static final PostfixItemsCompletionProvider instance = new PostfixItemsCompletionProvider();

  private PostfixItemsCompletionProvider() { }

  public void addCompletions(
    @NotNull final CompletionParameters parameters,
    @NotNull final ProcessingContext context,
    @NotNull final CompletionResultSet resultSet) {

    final PostfixTemplatesManager templatesManager =
      ApplicationManager.getApplication().getComponent(PostfixTemplatesManager.class);

    final PsiElement positionElement = parameters.getPosition();
    final boolean forceMode = !parameters.isAutoPopup();

    final PostfixTemplateAcceptanceContext acceptanceContext =
      templatesManager.isAvailable(positionElement, forceMode);

    if (acceptanceContext != null) {
      for (final LookupElement lookupElement
          : templatesManager.collectTemplates(acceptanceContext)) {
        resultSet.addElement(lookupElement);
      }
    }
  }
}