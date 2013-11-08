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
    @NotNull CompletionParameters parameters, @NotNull ProcessingContext context,
    @NotNull CompletionResultSet resultSet) {

    PostfixTemplatesManager manager = ApplicationManager.getApplication().getComponent(PostfixTemplatesManager.class);

    PsiElement positionElement = parameters.getPosition();
    boolean forceMode = !parameters.isAutoPopup();

    PostfixTemplateAcceptanceContext acceptance = manager.isAvailable(positionElement, forceMode);

    if (acceptance != null)
      for (LookupElement lookupElement : manager.collectTemplates(acceptance))
        resultSet.addElement(lookupElement);
  }
}