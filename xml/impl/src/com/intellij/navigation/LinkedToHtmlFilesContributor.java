// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlLinkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class LinkedToHtmlFilesContributor extends RelatedToHtmlFilesContributor {
  @Override
  public void fillRelatedFiles(final @NotNull XmlFile xmlFile, final @NotNull Set<? super PsiFile> resultSet) {
    HtmlLinkUtil.processLinks(xmlFile, tag -> {
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
    });
  }

  @Override
  public String getGroupName() {
    return XmlBundle.message("html.related.linked.files.group");
  }
}
