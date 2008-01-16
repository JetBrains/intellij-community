package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lexer.OldXmlLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author Mike
 */
public class XmlPsiLexer extends OldXmlLexer{
  public XmlPsiLexer() {
  }

  public IElementType getTokenType() {
    IElementType type = super.getTokenType();

    if (type == XmlTokenType.XML_WHITE_SPACE) {
      return TokenType.WHITE_SPACE;
    }

    return type;
  }
}
