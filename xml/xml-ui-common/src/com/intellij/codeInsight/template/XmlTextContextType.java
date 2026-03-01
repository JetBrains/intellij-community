// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlText;
import com.intellij.xml.XmlUiBundle;
import org.jetbrains.annotations.NotNull;

public class XmlTextContextType extends TemplateContextType {

  public XmlTextContextType() {
    super(XmlUiBundle.message("xml.text"));
  }

  @Override
  public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
    PsiFile file = templateActionContext.getFile();
    int offset = templateActionContext.getStartOffset();
    if (!XmlContextType.isInXml(file, offset)) return false;
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    if (PsiTreeUtil.getParentOfType(element, XmlText.class, false) != null) {
      return true;
    }
    PsiElement parent = element.getParent();
    if (parent instanceof PsiErrorElement) {
      parent = parent.getParent();
    }
    return parent instanceof XmlDocument;
  }
}
