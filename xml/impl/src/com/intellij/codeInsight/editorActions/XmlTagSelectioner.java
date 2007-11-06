package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;

public class XmlTagSelectioner extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(PsiElement e) {
    return e instanceof XmlTag;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    PsiElement[] children = e.getChildren();

    addTagContentSelection(children, result, editorText);

    PsiElement prev = e.getPrevSibling();
    while (prev instanceof PsiWhiteSpace || prev instanceof XmlText || prev instanceof XmlComment) {
      if (prev instanceof XmlText && prev.getText().trim().length() > 0) break;
      if (prev instanceof XmlComment) {
        result.addAll(expandToWholeLine(editorText,
                                        new TextRange(prev.getTextRange().getStartOffset(),
                                                      e.getTextRange().getEndOffset()),
                                        false));
      }
      prev = prev.getPrevSibling();
    }

    return result;
  }

  private static void addTagContentSelection(final PsiElement[] children, final List<TextRange> result, final CharSequence editorText) {
    PsiElement first = null;
    PsiElement last = null;
    for (PsiElement child : children) {
      if (child instanceof XmlToken) {
        XmlToken token = (XmlToken)child;
        if (token.getTokenType() == XmlTokenType.XML_TAG_END) {
          first = token.getNextSibling();
        }
        if (token.getTokenType() == XmlTokenType.XML_END_TAG_START) {
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
  }
}