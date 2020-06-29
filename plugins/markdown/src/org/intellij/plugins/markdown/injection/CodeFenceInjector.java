// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
 import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceContentImpl;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.intellij.plugins.markdown.util.MarkdownPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CodeFenceInjector implements MultiHostInjector {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!(context instanceof MarkdownCodeFenceImpl)) {
      return;
    }
    if (PsiTreeUtil.findChildOfType(context, MarkdownCodeFenceContentImpl.class) == null) {
      return;
    }

    boolean isTopLevel = context.getParent().getNode().getElementType() == MarkdownElementTypes.MARKDOWN_FILE;

    final Language language = findLangForInjection(((MarkdownCodeFenceImpl)context));
    if (language == null || LanguageParserDefinitions.INSTANCE.forLanguage(language) == null) {
      return;
    }

    registrar.startInjecting(language);

    //if top-level code fence -- insert with one place
    if (isTopLevel && Registry.is("markdown.formatter.apply.to.code.fence")) {
      injectAsOnePlace(context, registrar);
    } else {
      //if code fence in some block (e.g. blockquote) -- insert via few places
      injectAsFewPlaces(context, registrar);
    }

    registrar.doneInjecting();
  }

  /**
   * Such code fence will make use of IntelliJ Formatter.
   *
   * But, the problem is that not all formatters are ready to work in
   * injected context, so we should do it with great care.
   */
  private static void injectAsOnePlace(PsiElement context, MultiHostRegistrar registrar) {
    boolean seenFirst = false;
    int startOffset = -1;
    int length = 0;
    for (PsiElement child = context.getFirstChild().getNextSibling().getNextSibling();
         child != null && child.getNode().getElementType() != MarkdownTokenTypes.CODE_FENCE_END;
         child = child.getNextSibling()) {
      if (!seenFirst) {
        startOffset = child.getStartOffsetInParent();
        seenFirst = true;
      }
      length += child.getTextLength();
    }
    registrar.addPlace(null, null, ((MarkdownCodeFenceImpl)context), TextRange.from(startOffset, length));
  }

  private static void injectAsFewPlaces(PsiElement context, MultiHostRegistrar registrar) {
    for (PsiElement child = context.getFirstChild().getNextSibling().getNextSibling(); child != null; child = child.getNextSibling()) {
      if (MarkdownPsiUtil.isNewLine(child)) {
        registrar.addPlace(null, null, ((MarkdownCodeFenceImpl)context), TextRange.from(child.getStartOffsetInParent(), 1));
        continue;
      }

      if (child instanceof MarkdownCodeFenceContentImpl) {
        PsiElement nextSibling = child.getNextSibling();
        boolean includeNewLine = nextSibling != null && MarkdownPsiUtil.isNewLine(nextSibling);
        registrar.addPlace(null, null, ((MarkdownCodeFenceImpl)context), TextRange
          .from(child.getStartOffsetInParent(), includeNewLine ? child.getTextLength() + 1 : child.getTextLength()));

        if (includeNewLine) {
          //noinspection AssignmentToForLoopParameter
          child = nextSibling;
        }
      }
    }
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(MarkdownCodeFenceImpl.class);
  }

  @Nullable
  protected Language findLangForInjection(@NotNull MarkdownCodeFenceImpl element) {
    final String fenceLanguage = element.getFenceLanguage();
    if (fenceLanguage == null) {
      return null;
    }
    return guessLanguageByFenceLang(fenceLanguage);
  }

  @Nullable
  private static Language guessLanguageByFenceLang(@NotNull String langName) {
    if (MarkdownApplicationSettings.getInstance().isDisableInjections()) {
      return null;
    }
    else {
      return LanguageGuesser.INSTANCE.guessLanguage(langName);
    }
  }
}
