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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlElementContextType extends TemplateContextType {

  public XmlElementContextType() {
    super("XML_TAG", XmlBundle.message("xml.tag"), XmlContextType.class);
  }

  @Override
  public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
    PsiFile file = templateActionContext.getFile();
    int startOffset = templateActionContext.getStartOffset();
    if (!XmlContextType.isInXml(file, startOffset)) return false;
    
    return isInXmlElementContext(templateActionContext);
  }

  public static boolean isInXmlElementContext(@NotNull TemplateActionContext templateActionContext) {
    int startOffset = templateActionContext.getStartOffset();
    int endOffset = templateActionContext.getEndOffset();
    PsiElement parent = findCommonParent(templateActionContext);
    if (!(parent instanceof XmlTag)) return false;
    TextRange range = parent.getTextRange();
    return range.getStartOffset() >= startOffset && range.getEndOffset() <= endOffset;
  }
  
  @Nullable
  public static PsiElement findCommonParent(@NotNull TemplateActionContext templateActionContext) {
    PsiFile file = templateActionContext.getFile();
    int startOffset = templateActionContext.getStartOffset();
    int endOffset = templateActionContext.getEndOffset();
    if (endOffset <= startOffset) return null;

    PsiElement start = file.findElementAt(startOffset);
    PsiElement end = file.findElementAt(endOffset - 1);
    if (start instanceof PsiWhiteSpace) {
      start = start.getNextSibling();
    }
    if (end instanceof PsiWhiteSpace) {
      end = end.getPrevSibling();
    }
    if (start == null || end == null) return null;
    return PsiTreeUtil.findCommonParent(start, end);
  }
}
