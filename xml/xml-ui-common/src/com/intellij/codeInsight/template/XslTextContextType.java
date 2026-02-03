// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlUiBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XslTextContextType extends TemplateContextType {
  public XslTextContextType() {
    super(XmlUiBundle.message("dialog.edit.template.checkbox.xsl.text"));
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    if (isXslOrXsltFile(file)) {
      PsiElement element = file.findElementAt(offset);
      return element == null || HtmlTextContextType.isInContext(element);
    }
    return false;
  }

  public static boolean isXslOrXsltFile(@Nullable PsiFile file) {
    return file != null && file.getFileType() == XmlFileType.INSTANCE
        && (FileUtilRt.extensionEquals(file.getName(), "xsl") || FileUtilRt.extensionEquals(file.getName(), "xslt"));
  }
}
