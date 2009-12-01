/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.usageView;

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class XmlUsageViewDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (location instanceof UsageViewShortNameLocation) {
      if (element instanceof XmlAttributeValue) {
        return ((XmlAttributeValue)element).getValue();
      }
    }

    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof XmlTag) {
        return ((XmlTag)element).getName();
      }
      else if (element instanceof XmlAttributeValue) {
        return ((XmlAttributeValue)element).getValue();
      }
    }

    if (location instanceof HighlightUsagesDescriptionLocation) {
      if (element instanceof PsiPresentableMetaData) {
        return null;
      }
      if (element instanceof PsiFile) {
        return "File";
      }

      final FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(element.getLanguage());
      if (provider.canFindUsagesFor(element)) {
        return provider.getType(element);
      }
    }
    return null;
  }
}
