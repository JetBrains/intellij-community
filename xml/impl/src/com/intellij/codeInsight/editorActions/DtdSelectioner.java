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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttlistDecl;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;

import java.util.ArrayList;
import java.util.List;

public class DtdSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof XmlAttlistDecl || e instanceof XmlElementDecl;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    PsiElement[] children = e.getChildren();

    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement child : children) {
      if (child instanceof XmlToken) {
        XmlToken token = (XmlToken)child;
        if (token.getTokenType() == XmlTokenType.XML_TAG_END) {
          last = token;
          break;
        }
        if (token.getTokenType() == XmlTokenType.XML_ELEMENT_DECL_START ||
            token.getTokenType() == XmlTokenType.XML_ATTLIST_DECL_START
           ) {
          first = token;
        }
      }
    }

    List<TextRange> result = new ArrayList<>(1);
    if (first != null && last != null) {
      final int offset = last.getTextRange().getEndOffset() + 1;
        result.addAll(ExtendWordSelectionHandlerBase.expandToWholeLine(editorText,
                                        new TextRange(first.getTextRange().getStartOffset(), offset < editorText.length() ? offset:editorText.length()),
                                        false));
    }

    return result;
  }
}