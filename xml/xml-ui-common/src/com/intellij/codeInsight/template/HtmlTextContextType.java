// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlUiBundle;
import org.jetbrains.annotations.NotNull;

public class HtmlTextContextType extends TemplateContextType {
  public HtmlTextContextType() {
    super(XmlUiBundle.message("dialog.edit.template.checkbox.html.text"));
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    Language language = PsiUtilCore.getLanguageAtOffset(file, offset);
    if (!HtmlContextType.isMyLanguage(language)) {
      return false;
    }
    PsiElement element = file.getViewProvider().findElementAt(offset, language);
    return element == null || isInContext(element);
  }

  public static boolean isInContext(@NotNull PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, XmlComment.class) != null 
        && element.getNode().getElementType() != XmlTokenType.XML_COMMENT_START) {
      return false;
    }
    if (PsiTreeUtil.getParentOfType(element, XmlText.class) != null) {
      return true;
    }
    if (element.getNode().getElementType() == XmlTokenType.XML_START_TAG_START) {
      return true;
    }
    PsiElement parent = element.getParent();
    if (parent instanceof PsiErrorElement) {
      parent = parent.getParent();
    }
    return parent instanceof XmlDocument;
  }
}
