// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.openapi.paths.GlobalPathReferenceProvider;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

final class YAMLWebReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      or(psiElement(YAMLQuotedText.class),
         psiElement(YAMLPlainTextImpl.class)),
      new PsiReferenceProvider() {
        @Override
        public boolean acceptsTarget(@NotNull PsiElement target) {
          return false; // web references do not point to any real PsiElement
        }

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
          if (!(element instanceof YAMLScalar scalarElement)) return PsiReference.EMPTY_ARRAY;
          if (!element.textContains(':')) return PsiReference.EMPTY_ARRAY;

          LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = scalarElement.createLiteralTextEscaper();
          if (!escaper.isOneLine()) return PsiReference.EMPTY_ARRAY;

          TextRange textRange = escaper.getRelevantTextRange();
          if (textRange.isEmpty()) return PsiReference.EMPTY_ARRAY;

          String textValue = scalarElement.getTextValue();

          if (GlobalPathReferenceProvider.isWebReferenceUrl(textValue)) {
            return new PsiReference[]{new WebReference(scalarElement, textRange, textValue)};
          }

          return PsiReference.EMPTY_ARRAY;
        }
      }, PsiReferenceRegistrar.LOWER_PRIORITY);
  }
}
