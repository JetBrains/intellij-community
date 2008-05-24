package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttlistDecl;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class DtdSelectioner implements ExtendWordSelectionHandler {
  public boolean canSelect(PsiElement e) {
    return e instanceof XmlAttlistDecl || e instanceof XmlElementDecl;
  }

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

    List<TextRange> result = new ArrayList<TextRange>(1);
    if (first != null && last != null) {
      final int offset = last.getTextRange().getEndOffset() + 1;
        result.addAll(ExtendWordSelectionHandlerBase.expandToWholeLine(editorText,
                                        new TextRange(first.getTextRange().getStartOffset(), offset < editorText.length() ? offset:editorText.length()),
                                        false));
    }

    return result;
  }
}