// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;

public class XmlLexer extends MergingLexerAdapter {
  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(XmlTokenType.XML_DATA_CHARACTERS,
                                                                  XmlTokenType.XML_TAG_CHARACTERS,
                                                                  XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
                                                                  XmlTokenType.XML_PI_TARGET,
                                                                  XmlTokenType.XML_COMMENT_CHARACTERS);

  public XmlLexer() {
    this(false);
  }

  public XmlLexer(final boolean conditionalCommentsSupport) {
    this(new _XmlLexer(new __XmlLexer(null), conditionalCommentsSupport));
  }

  public XmlLexer(Lexer baseLexer) {
    super(baseLexer, TOKENS_TO_MERGE);
  }
}
