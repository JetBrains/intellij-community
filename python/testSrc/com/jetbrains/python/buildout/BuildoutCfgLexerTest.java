// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.buildout.config.lexer._BuildoutCfgFlexLexer;
import junit.framework.TestCase;

import java.io.IOException;

public class BuildoutCfgLexerTest extends TestCase {
  private static final Logger LOG = Logger.getInstance(BuildoutCfgLexerTest.class);
  public void testSimple() throws IOException {
    doTest("[buildout]\n" +
           "develop = .\n" +
           "parts =\n" +
           "  xprompt\n" +
           "  test\n",
           "[[, []",
           "[buildout, SECTION_NAME]",
           "[], ]]",
           "[\n, WHITESPACE]",
           "[develop , KEY_CHARACTERS]",
           "[=, KEY_VALUE_SEPARATOR]",
           "[ ., VALUE_CHARACTERS]",
           "[\n, WHITESPACE]",
           "[parts , KEY_CHARACTERS]",
           "[=, KEY_VALUE_SEPARATOR]",
           "[\n, WHITESPACE]",
           "[  xprompt, VALUE_CHARACTERS]",
           "[\n, WHITESPACE]",
           "[  test, VALUE_CHARACTERS]",
           "[\n, WHITESPACE]");
  }

  public void testComments() throws IOException {
    doTest("# comment\n" +
           "; comment\n" +
           "[buildout]\n" +
           "develop = value ; comment\n",
           "[# comment\n, COMMENT]",
           "[; comment\n, COMMENT]",
           "[[, []",
           "[buildout, SECTION_NAME]",
           "[], ]]",
           "[\n, WHITESPACE]",
           "[develop , KEY_CHARACTERS]",
           "[=, KEY_VALUE_SEPARATOR]",
           "[ value, VALUE_CHARACTERS]",
           "[ ; comment\n, COMMENT]"
           );
  }

  public void testSpaces() throws IOException {
    doTest( "[buildout]\n" +
           "parts = python django console_scripts\n",
           "[[, []",
           "[buildout, SECTION_NAME]",
           "[], ]]",
           "[\n, WHITESPACE]",
           "[parts , KEY_CHARACTERS]",
           "[=, KEY_VALUE_SEPARATOR]",
           "[ python django console_scripts, VALUE_CHARACTERS]",
           "[\n, WHITESPACE]"
           );
  }


  private static void doTest(String text, String... expected) throws IOException {
    _BuildoutCfgFlexLexer lexer = createLexer(text);
    for (String expectedTokenText : expected) {
      IElementType type = lexer.advance();
      if (type == null) {
        fail("Not enough tokens");
      }
      String tokenText = "[" + lexer.yytext() + ", " + type + "]";
      assertEquals(expectedTokenText, tokenText);
    }
    IElementType type = lexer.advance();
    if (type != null) {
      do {
        String tokenText = "[" + lexer.yytext() + ", " + type + "]";
        LOG.debug(tokenText);
        type = lexer.advance();
      }
      while (type != null);
      fail("Too many tokens");
    }
  }

  public static _BuildoutCfgFlexLexer createLexer(String text) {
    _BuildoutCfgFlexLexer lexer = new _BuildoutCfgFlexLexer(null);
    lexer.reset(text, 0, text.length(), _BuildoutCfgFlexLexer.YYINITIAL);
    return lexer;
  }
}
