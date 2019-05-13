/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class XslTextContextType extends TemplateContextType {
  public XslTextContextType() {
    super("XSL_TEXT", CodeInsightBundle.message("dialog.edit.template.checkbox.xsl.text"), XmlContextType.class);
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
    return file != null && file.getFileType() == StdFileTypes.XML
        && (FileUtilRt.extensionEquals(file.getName(), "xsl") || FileUtilRt.extensionEquals(file.getName(), "xslt"));
  }
}
