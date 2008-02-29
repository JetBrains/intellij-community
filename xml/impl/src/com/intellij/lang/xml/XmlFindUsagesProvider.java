package com.intellij.lang.xml;

import com.intellij.find.impl.HelpID;
import com.intellij.lang.LangBundle;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class XmlFindUsagesProvider implements FindUsagesProvider {

  public boolean canFindUsagesFor(@NotNull PsiElement element) {
    return element instanceof XmlElementDecl ||
           element instanceof XmlAttributeDecl ||
           element instanceof XmlEntityDecl ||
           element instanceof XmlTag ||
           element instanceof XmlAttributeValue ||
           element instanceof PsiFile ||
           (PsiUtil.isInJspFile(element) && element instanceof XmlComment);
  }

  @NotNull
  public String getType(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      final PsiMetaData metaData = ((XmlTag)element).getMetaData();
      if (metaData != null && metaData.getDeclaration() instanceof XmlTag) {
        return ((XmlTag)metaData.getDeclaration()).getName();
      }
      return LangBundle.message("xml.terms.xml.tag");
    }
    if (element instanceof XmlElementDecl) {
      return LangBundle.message("xml.terms.tag");
    }
    else if (element instanceof XmlAttributeDecl) {
      return LangBundle.message("xml.terms.attribute");
    }
    else if (element instanceof XmlAttributeValue) {
      return LangBundle.message("xml.terms.attribute.value");
    }
    else if (element instanceof XmlEntityDecl) {
      return LangBundle.message("xml.terms.entity");
    }
    else if (element instanceof XmlAttribute) {
      return LangBundle.message("xml.terms.attribute");
    } else if (element instanceof XmlComment) {
      return LangBundle.message("xml.terms.variable");
    }
    throw new IllegalArgumentException("Cannot get type for " + element);
  }

  public String getHelpId(@NotNull PsiElement element) {
    return HelpID.FIND_OTHER_USAGES;
  }

  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      return ((XmlTag)element).getName();
    }

    if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }

    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    } else {
      return element.getText();
    }
  }

  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    if (element instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)element;
      final PsiMetaData metaData = xmlTag.getMetaData();
      final String name = metaData != null ? UsageViewUtil.getMetaDataName(metaData) : xmlTag.getName();
      return UsageViewBundle.message("usage.target.xml.tag.of.file", metaData == null ? "<" + name + ">" : name, xmlTag.getContainingFile().getName());
    }
    else if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    } else {
      return element.getText();
    }
  }

  public WordsScanner getWordsScanner() {
    return null;
  }
}
