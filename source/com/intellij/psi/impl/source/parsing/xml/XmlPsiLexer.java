package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lexer.OldXmlLexer;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;

/**
 * @author Mike
 */
public class XmlPsiLexer extends OldXmlLexer{
  public XmlPsiLexer() {
  }

  public IElementType getTokenType() {
    IElementType type = super.getTokenType();

    if (type == XmlTokenType.XML_WHITE_SPACE) {
      return ElementType.WHITE_SPACE;
    }

    return type;
  }
}
