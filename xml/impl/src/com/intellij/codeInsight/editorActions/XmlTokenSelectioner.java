package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

class XmlTokenSelectioner extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(PsiElement e) {
    return e instanceof XmlToken &&
           !HtmlSelectioner.canSelectElement(e);
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    XmlToken token = (XmlToken)e;

    if (token.getTokenType() != XmlTokenType.XML_DATA_CHARACTERS &&
        token.getTokenType() != XmlTokenType.XML_START_TAG_START &&
        token.getTokenType() != XmlTokenType.XML_END_TAG_START
      ) {
      List<TextRange> ranges = super.select(e, editorText, cursorOffset, editor);
      SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, ranges);
      return ranges;
    }
    else {
      List<TextRange> result = new ArrayList<TextRange>();
      SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, result);
      return result;
    }
  }
}