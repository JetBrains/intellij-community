// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    super(XmlBundle.message("xml.tag"));
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
  
  public static @Nullable PsiElement findCommonParent(@NotNull TemplateActionContext templateActionContext) {
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
