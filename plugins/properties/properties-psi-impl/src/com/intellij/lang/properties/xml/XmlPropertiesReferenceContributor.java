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
package com.intellij.lang.properties.xml;

import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.patterns.XmlPatterns;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 9/15/11
 */
public class XmlPropertiesReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withLocalName("key"),
                                        new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(element.getContainingFile());
        if (propertiesFile == null) return PsiReference.EMPTY_ARRAY;
        XmlProperty property = new XmlProperty(PsiTreeUtil.getParentOfType(element, XmlTag.class), (XmlPropertiesFileImpl)propertiesFile);
        return new PsiReference[] {new PsiReferenceBase.Immediate<>(element, PomService.convertToPsi(property))};
      }
    });
  }
}
