// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

public class XsltChangeLocalityDetector implements ChangeLocalityDetector {
  @Override
  public PsiElement getChangeHighlightingDirtyScopeFor(@NotNull PsiElement changedElement) {
    try {
      if (changedElement instanceof XmlToken && changedElement.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
        final PsiElement grandParent = changedElement.getParent().getParent();
        if (grandParent instanceof XmlAttribute) {
          if (XsltSupport.isXPathAttribute((XmlAttribute)grandParent)) {
            return grandParent;
          }
        }
      } else if (changedElement instanceof XmlTag && XsltSupport.isTemplate((XmlTag)changedElement, false)) {
        return changedElement;
      }
    } catch (NullPointerException e) {
      // sth was null
    }
    return null;
  }
}