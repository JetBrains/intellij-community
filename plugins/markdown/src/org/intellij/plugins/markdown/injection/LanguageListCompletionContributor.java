// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.DeferredIconImpl;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class LanguageListCompletionContributor extends CompletionContributor {

  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    if (context.getFile() instanceof MarkdownFile) {
      context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER + "\n");
    }
  }

  @Override
  public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
    return typeChar == '`' && position.getNode().getElementType() == MarkdownTokenTypes.CODE_FENCE_START;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement completionElement = parameters.getPosition();
    if (PsiUtilCore.getElementType(completionElement) == MarkdownTokenTypes.FENCE_LANG) {
      doFillVariants(parameters, result);
    }
  }

  private static void doFillVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    for (CodeFenceLanguageProvider provider : LanguageGuesser.INSTANCE.getCodeFenceLanguageProviders()) {
      final List<LookupElement> lookups = provider.getCompletionVariantsForInfoString(parameters);
      for (LookupElement lookupElement : lookups) {
        result.addElement(LookupElementDecorator.withInsertHandler(lookupElement, (context, item) -> {
          new MyInsertHandler(parameters).handleInsert(context, item);
          lookupElement.handleInsert(context);
        }));
      }
    }

    for (Map.Entry<String, Language> entry : LanguageGuesser.INSTANCE.getLangToLanguageMap().entrySet()) {
      final Language language = entry.getValue();

      final LookupElementBuilder lookupElementBuilder =
        LookupElementBuilder.create(entry.getKey())
          .withIcon(createLanguageIcon(language))
          .withTypeText(language.getDisplayName(), true)
          .withInsertHandler(new MyInsertHandler(parameters));

      result.addElement(lookupElementBuilder);
    }
  }

  @NotNull
  public static Icon createLanguageIcon(@NotNull Language language) {
    return new DeferredIconImpl<>(null, language, true, curLanguage -> {
      final LanguageFileType fileType = curLanguage.getAssociatedFileType();
      return fileType != null ? fileType.getIcon() : null;
    });
  }

  public static boolean isInMiddleOfUncollapsedFence(@Nullable PsiElement element, int offset) {
    if (element == null) {
      return false;
    }
    if (PsiUtilCore.getElementType(element) == MarkdownTokenTypes.CODE_FENCE_START) {
      final TextRange range = element.getTextRange();
      return range.getStartOffset() + range.getEndOffset() == offset * 2;
    }
    if (PsiUtilCore.getElementType(element) == MarkdownTokenTypes.TEXT
        && PsiUtilCore.getElementType(element.getParent()) == MarkdownElementTypes.CODE_SPAN) {
      final TextRange range = element.getTextRange();
      final TextRange parentRange = element.getParent().getTextRange();

      return range.getStartOffset() - parentRange.getStartOffset() == parentRange.getEndOffset() - range.getEndOffset();
    }

    return false;
  }

  private static class MyInsertHandler implements InsertHandler<LookupElement> {
    private final CompletionParameters myParameters;

    MyInsertHandler(CompletionParameters parameters) {
      myParameters = parameters;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      if (isInMiddleOfUncollapsedFence(myParameters.getOriginalPosition(), context.getStartOffset())) {
        context.getDocument().insertString(context.getTailOffset(), "\n\n");
        context.getEditor().getCaretModel().moveCaretRelatively(1, 0, false, false, false);
      }
    }
  }
}
