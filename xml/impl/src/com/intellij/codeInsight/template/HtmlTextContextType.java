/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class HtmlTextContextType extends TemplateContextType {
  public HtmlTextContextType() {
    super("HTML_TEXT", CodeInsightBundle.message("dialog.edit.template.checkbox.html.text"), HtmlContextType.class);
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    if (!HtmlContextType.isMyLanguage(file.getLanguage())) {
      return false;
    }
    PsiElement element = file.findElementAt(offset);
    return element == null || isInContext(element);
  }

  public static boolean isInContext(@NotNull PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, XmlComment.class) != null) {
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
