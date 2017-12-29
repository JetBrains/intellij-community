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
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XmlCDATAContentSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof CompositePsiElement &&
           ((CompositePsiElement)e).getElementType() == XmlElementType.XML_CDATA;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    PsiElement[] children = e.getChildren();

    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement child : children) {
      if (child instanceof XmlToken) {
        XmlToken token = (XmlToken)child;
        if (token.getTokenType() == XmlTokenType.XML_CDATA_START) {
          first = token.getNextSibling();
        }
        if (token.getTokenType() == XmlTokenType.XML_CDATA_END) {
          last = token.getPrevSibling();
          break;
        }
      }
    }

    if (first != null && last != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(first.getTextRange().getStartOffset(),
                                                    last.getTextRange().getEndOffset()),
                                      false));
    }

    return result;
  }
}