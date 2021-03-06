/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlText;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlTextContextType extends TemplateContextType {

  public XmlTextContextType() {
    super("XML_TEXT", XmlBundle.message("xml.text"), XmlContextType.class);
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
