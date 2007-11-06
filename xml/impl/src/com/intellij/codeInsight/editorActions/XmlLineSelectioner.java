package com.intellij.codeInsight.editorActions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;

/**
 * @author yole
 */
public class XmlLineSelectioner extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(final PsiElement e) {
    return e instanceof XmlToken && ((XmlToken)e).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS;
  }

  @Override
  public List<TextRange> select(final PsiElement e, final CharSequence editorText, final int cursorOffset, final Editor editor) {
    return SelectWordUtil.PlainTextLineSelectioner.selectPlainTextLine(e, editorText, cursorOffset);
  }
}