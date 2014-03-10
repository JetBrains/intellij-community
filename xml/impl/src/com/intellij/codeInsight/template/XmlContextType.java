/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class XmlContextType extends TemplateContextType {
  public XmlContextType() {
    super("XML", CodeInsightBundle.message("dialog.edit.template.checkbox.xml"));
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
}
