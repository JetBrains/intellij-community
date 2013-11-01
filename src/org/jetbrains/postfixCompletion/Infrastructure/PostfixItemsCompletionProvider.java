package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
      final List<LookupElement> lookupElements = templatesManager.collectTemplates(acceptanceContext);
      for (final LookupElement lookupElement : lookupElements) {
        resultSet.addElement(lookupElement);
      }
    }
  }
}
