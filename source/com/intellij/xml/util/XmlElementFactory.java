/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.xml.util;

import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;

/**
 * @author mike
 */
public class XmlElementFactory {
  private XmlElementFactory() {
  }

  public static XmlText createXmlTextFromText(PsiManager psiManager, String text) throws IncorrectOperationException {
    return psiManager.getElementFactory().createTagFromText("<foo>" + text + "</foo>").getValue().getTextElements()[0];
  }
}
