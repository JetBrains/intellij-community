/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.xml;

import com.intellij.lang.LangBundle;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
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

  @Override
  public String getHelpId(@NotNull PsiElement element) {
    return com.intellij.lang.HelpID.FIND_OTHER_USAGES;
  }

  @Override
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
    }
    return element.getText();
  }

  @Override
  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    if (element instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)element;
      final PsiMetaData metaData = xmlTag.getMetaData();
      final String name = metaData != null ? DescriptiveNameUtil.getMetaDataName(metaData) : xmlTag.getName();

      String presentableName = metaData == null ? "<" + name + ">" : name;
      return presentableName+" of file "+xmlTag.getContainingFile().getName();
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
  public WordsScanner getWordsScanner() {
    return null;
  }
}
