/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.navigation;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.xml.util.HtmlLinkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class LinkedToHtmlFilesContributor extends RelatedToHtmlFilesContributor {
  @Override
  public void fillRelatedFiles(@NotNull final XmlFile xmlFile, @NotNull final Set<PsiFile> resultSet) {
    HtmlLinkUtil.processLinks(xmlFile, new Processor<XmlTag>() {
      @Override
      public boolean process(XmlTag tag) {
        final XmlAttribute attribute = tag.getAttribute("href");
        if (attribute == null) {
          return true;
        }

        final XmlAttributeValue link = attribute.getValueElement();
        if (link == null) {
          return true;
        }

        for (PsiReference reference : link.getReferences()) {
          if (reference instanceof PsiPolyVariantReference) {
            final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);

            for (ResolveResult result : results) {
              final PsiElement resolvedElement = result.getElement();
              if (resolvedElement instanceof PsiFile) {
                resultSet.add((PsiFile)resolvedElement);
              }
            }
          }
          else {
            final PsiElement resolvedElement = reference.resolve();
            if (resolvedElement instanceof PsiFile) {
              resultSet.add((PsiFile)resolvedElement);
            }
          }
        }
        return true;
      }
    });
  }

  @Override
  public String getGroupName() {
    return "Linked files";
  }
}
