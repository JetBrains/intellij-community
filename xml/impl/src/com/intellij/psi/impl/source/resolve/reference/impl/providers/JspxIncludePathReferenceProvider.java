// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class JspxIncludePathReferenceProvider extends PsiReferenceProvider implements CustomizableReferenceProvider {
  private @Nullable Map<CustomizationKey, Object> myOptions;

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, final @NotNull ProcessingContext context) {
    if (element instanceof XmlAttributeValue attributeValue) {

      String valueString = attributeValue.getValue();
      if(valueString.indexOf('?') >= 0)
        return getReferencesByString(valueString.substring(0, valueString.indexOf('?')), attributeValue, 1);
      return getReferencesByString(valueString, attributeValue, 1);
    } else if (element instanceof XmlTag tag) {

      final XmlTagValue value = tag.getValue();
      final String text = value.getText();
      String trimmedText = text.trim();

      return getReferencesByString(
        trimmedText,
        tag, value.getTextRange().getStartOffset() + text.indexOf(trimmedText) - element.getTextOffset()
      );
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private PsiReference @NotNull [] getReferencesByString(String str, @NotNull PsiElement position, int offsetInPosition) {
    return getFileReferencesFromString(str, position, offsetInPosition, this);
  }

  public static PsiReference[] getFileReferencesFromString(final String str, @NotNull PsiElement position, final int offsetInPosition,
                                                           PsiReferenceProvider provider) {
    return getFileReferenceSet(str, position, offsetInPosition, provider).getAllReferences();
  }

  public static FileReferenceSet getFileReferenceSet(final String str, @NotNull PsiElement position,
                                                     final int offsetInPosition,
                                                     final PsiReferenceProvider provider) {
    return new FileReferenceSet(str, position, offsetInPosition, provider, true) {
      @Override
      protected boolean useIncludingFileAsContext() {
        return false;
      }
    };
  }

  @Override
  public @Nullable Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }
}
