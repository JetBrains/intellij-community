package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Sep 9, 2004
 * Time: 8:27:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class OldXmlLexer extends MergingLexerAdapter {
  private final static TokenSet TOKENS_TO_MERGE = TokenSet.create(new IElementType[]{
    XmlTokenType.XML_DATA_CHARACTERS,
    XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
    XmlTokenType.XML_PI_TARGET,
  });

  public OldXmlLexer() {
    super(new FlexAdapter(new _OldXmlLexer()), TOKENS_TO_MERGE);
  }
}
