// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class XmlContextType extends TemplateContextType {
  public XmlContextType() {
    super("XML", XmlBundle.message("dialog.edit.template.checkbox.xml"));
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return isInXml(file, offset);
  }

  public static boolean isInXml(PsiFile file, int offset) {
    return file.getLanguage().isKindOf(XMLLanguage.INSTANCE) && !isEmbeddedContent(file, offset) &&
           !HtmlContextType.isMyLanguage(PsiUtilCore.getLanguageAtOffset(file, offset)) &&
           file.getFileType() != StdFileTypes.JSPX && file.getFileType() != StdFileTypes.JSP;
  }

  public static boolean isEmbeddedContent(@NotNull final PsiFile file, final int offset) {
    Language languageAtOffset = PsiUtilCore.getLanguageAtOffset(file, offset);
    return !(languageAtOffset.isKindOf(XMLLanguage.INSTANCE) || languageAtOffset instanceof XMLLanguage);
  }

  @Nullable
  @Override
  public SyntaxHighlighter createHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(XmlFileType.INSTANCE, null, null);
  }
}
