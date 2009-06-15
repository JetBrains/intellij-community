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
      return provider.getType(element);
    }
    return null;
  }
}
