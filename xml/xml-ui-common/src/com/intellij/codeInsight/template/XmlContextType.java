// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.xml.XmlUiBundle;
import com.intellij.xml.util.JspFileTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlContextType extends TemplateContextType {
  public XmlContextType() {
    super(XmlUiBundle.message("dialog.edit.template.checkbox.xml"));
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return isInXml(file, offset);
  }

  public static boolean isInXml(PsiFile file, int offset) {
    return file.getLanguage().isKindOf(XMLLanguage.INSTANCE) && !isEmbeddedContent(file, offset) &&
           !HtmlContextType.isMyLanguage(PsiUtilCore.getLanguageAtOffset(file, offset)) &&
           !JspFileTypeUtil.isJspOrJspX(file);
  }

  public static boolean isEmbeddedContent(final @NotNull PsiFile file, final int offset) {
    Language languageAtOffset = PsiUtilCore.getLanguageAtOffset(file, offset);
    return !(languageAtOffset.isKindOf(XMLLanguage.INSTANCE) || languageAtOffset instanceof XMLLanguage);
  }

  @Override
  public @Nullable SyntaxHighlighter createHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(XmlFileType.INSTANCE, null, null);
  }
}
