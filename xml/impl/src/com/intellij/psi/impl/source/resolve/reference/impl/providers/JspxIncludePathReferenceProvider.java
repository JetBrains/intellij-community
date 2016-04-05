/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author ven
 */
public class JspxIncludePathReferenceProvider extends PsiReferenceProvider implements CustomizableReferenceProvider {
  @Nullable private Map<CustomizationKey, Object> myOptions;

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    if (element instanceof XmlAttributeValue) {
      final XmlAttributeValue attributeValue = ((XmlAttributeValue)element);

      String valueString = attributeValue.getValue();
      if(valueString.indexOf('?') >= 0)
        return getReferencesByString(valueString.substring(0, valueString.indexOf('?')), attributeValue, 1);
      return getReferencesByString(valueString, attributeValue, 1);
    } else if (element instanceof XmlTag) {
      final XmlTag tag = ((XmlTag)element);

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

  @NotNull
  private PsiReference[] getReferencesByString(String str, @NotNull PsiElement position, int offsetInPosition) {
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
  public void setOptions(@Nullable Map<CustomizationKey, Object> options) {
    myOptions = options;
  }

  @Override
  @Nullable
  public Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }
}
