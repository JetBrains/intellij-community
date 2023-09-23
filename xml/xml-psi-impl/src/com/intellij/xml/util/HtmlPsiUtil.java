// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.Nullable;

public final class HtmlPsiUtil {
  public static @Nullable XmlDocument getRealXmlDocument(@Nullable XmlDocument doc) {
    if (doc == null) return null;
    final PsiFile containingFile = doc.getContainingFile();

    final PsiFile templateFile = TemplateLanguageUtil.getTemplateFile(containingFile);
    if (templateFile instanceof XmlFile) {
      return ((XmlFile)templateFile).getDocument();
    }
    return doc;
  }
}
