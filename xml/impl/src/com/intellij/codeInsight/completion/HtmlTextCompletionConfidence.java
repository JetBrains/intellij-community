/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public class HtmlTextCompletionConfidence extends CompletionConfidence {
  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    return shouldSkipAutopopupInHtml(contextElement, offset) ? ThreeState.YES : ThreeState.UNSURE;
  }

  public static boolean shouldSkipAutopopupInHtml(@NotNull PsiElement contextElement, int offset) {
    ASTNode node = contextElement.getNode();
    if (node != null && node.getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
      PsiElement parent = contextElement.getParent();
      if (parent instanceof XmlText || parent instanceof XmlDocument) {
        String contextElementText = contextElement.getText();
        int endOffset = offset - contextElement.getTextRange().getStartOffset();
        String prefix = contextElementText.substring(0, Math.min(contextElementText.length(), endOffset));
        return !StringUtil.startsWithChar(prefix, '<') && !StringUtil.startsWithChar(prefix, '&');
      }
    }
    return false;
  }
}
