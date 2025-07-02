// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml;

import com.intellij.lang.HelpID;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.*;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;

public class XmlFindUsagesProvider implements FindUsagesProvider {
  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement element) {
    return element instanceof XmlElementDecl ||
           element instanceof XmlAttributeDecl ||
           element instanceof XmlEntityDecl ||
           element instanceof XmlTag ||
           element instanceof XmlAttributeValue ||
           element instanceof PsiFile ||
           element instanceof XmlComment;
  }

  @Override
  public @NotNull String getType(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      final PsiMetaData metaData = ((XmlTag)element).getMetaData();
      if (metaData != null && metaData.getDeclaration() instanceof XmlTag) {
        return ((XmlTag)metaData.getDeclaration()).getName();
      }
      return XmlPsiBundle.message("xml.terms.xml.tag");
    }
    if (element instanceof XmlElementDecl) {
      return XmlPsiBundle.message("xml.terms.tag");
    }
    else if (element instanceof XmlAttributeDecl) {
      return XmlPsiBundle.message("xml.terms.attribute");
    }
    else if (element instanceof XmlAttributeValue) {
      return XmlPsiBundle.message("xml.terms.attribute.value");
    }
    else if (element instanceof XmlEntityDecl) {
      return XmlPsiBundle.message("xml.terms.entity");
    }
    else if (element instanceof XmlAttribute) {
      return XmlPsiBundle.message("xml.terms.attribute");
    } else if (element instanceof XmlComment) {
      return XmlPsiBundle.message("xml.terms.variable");
    }
    throw new IllegalArgumentException("Cannot get type for " + element);
  }

  @Override
  public String getHelpId(@NotNull PsiElement element) {
    return HelpID.FIND_OTHER_USAGES;
  }

  @Override
  public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      return ((XmlTag)element).getName();
    }

    if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }

    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }
    return element.getText();
  }

  @Override
  public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    if (element instanceof XmlTag xmlTag) {
      final PsiMetaData metaData = xmlTag.getMetaData();
      final String name = metaData != null ? DescriptiveNameUtil.getMetaDataName(metaData) : xmlTag.getName();

      String presentableName = metaData == null ? "<" + name + ">" : name;
      return XmlPsiBundle.message("xml.find.usages.presentable.name.of.containing.file", presentableName, xmlTag.getContainingFile().getName());
    }
    if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }
    return element.getText();
  }
}
