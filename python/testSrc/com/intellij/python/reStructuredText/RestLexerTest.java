// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.psi.tree.IElementType;
import com.intellij.python.reStructuredText.lexer._RestFlexLexer;
import junit.framework.TestCase;

import java.io.IOException;

/**
 * User : catherine
 */
public class RestLexerTest extends TestCase {

  public void testTitle() throws IOException {
    doTest("""

             .. _quickstart:

             Quick start guide
             =================
             """,
           "[\n, WHITESPACE]",
           "[.. , EXPLISIT_MARKUP_START]",
           "[_quickstart:, HYPERLINK]",
           "[\n, WHITESPACE]",
           "[\n, WHITESPACE]",
           """
             [Quick start guide
             =================
             , TITLE]"""
    );
  }

  public void testDirective() throws IOException {
    doTest("""

             .. note::
                 Please""",
           "[\n, WHITESPACE]",
           "[.. , EXPLISIT_MARKUP_START]",
           "[note::, DIRECTIVE]",
           "[\n, WHITESPACE]",
           "[    , LINE]",
           "[Please, LINE]"
    );
  }

  public void testSubstitution() throws IOException {
    doTest(".. |grappelli| replace:: Grappelli",
           "[.. , EXPLISIT_MARKUP_START]",
           "[|grappelli|, SUBSTITUTION]",
           "[ , WHITESPACE]",
           "[replace::, DIRECTIVE]",
           "[ Grappelli, LINE]"
    );
  }

  public void testField() throws IOException {
    doTest(".. figure:: image.png\n" +
           "   :width: 300pt",
           "[.. , EXPLISIT_MARKUP_START]",
           "[figure::, DIRECTIVE]",
           "[ image.png, LINE]",
           "[\n, WHITESPACE]",
           "[   , LINE]",
           "[:width:, FIELD]",
           "[ , LINE]",
           "[300pt, LINE]"
    );
  }

  public void testRole() throws IOException {                //PY-3810
    doTest("""
              role :roole1:`some text` :notparsed: text
             :list field:
              :second field:
             """,
           "[ , LINE]",
           "[role, LINE]",
           "[ , LINE]",
           "[:roole1:, FIELD]",
           "[`some text`, INTERPRETED]",
           "[ , LINE]",
           "[:, LINE]",
           "[notparsed, LINE]",
           "[:, LINE]",
           "[ , LINE]",
           "[text, LINE]",
           "[\n, WHITESPACE]",
           "[:list field:, FIELD]",
           "[\n, WHITESPACE]",
           "[ , LINE]",
           "[:second field:, FIELD]",
           "[\n, WHITESPACE]"
    );
  }

  public void testFootnote() throws IOException {
    doTest(".. [2] Random",
           "[.. , EXPLISIT_MARKUP_START]",
           "[[2], FOOTNOTE]",
           "[ , LINE]",
           "[Random, LINE]"
    );
  }

  public void testHyperlink() throws IOException {
    doTest(".. _`dvipng`: http://savannah.nongnu.org/projects/dvipng/",
           "[.. , EXPLISIT_MARKUP_START]",
           "[_`dvipng`:, HYPERLINK]",
           "[ , LINE]",
           "[http://savannah.nongnu.org/projects/dvipng/, DIRECT_HYPERLINK]"
    );
  }

  public void testItalics() throws IOException {
    doTest("*italics* highlighting",
           "[*italics*, ITALIC]",
           "[ , LINE]",
           "[highlighting, LINE]"
    );
  }

  public void testBold() throws IOException {
    doTest("**bold** highlighting",
           "[**bold**, BOLD]",
           "[ , LINE]",
           "[highlighting, LINE]"
    );
  }

  public void testShortHeading() throws IOException {
    doTest("DSF\n" +
           "===",
           "[DSF, LINE]",
           "[\n, WHITESPACE]",
           "[===, LINE]"
    );
  }

  public void testSubstitutions() throws IOException {
    doTest("""
             .. |end-user| replace:: :term:`user`
             .. |PNS ID| replace:: :term:`user`
             .. |PNS.ID| replace:: :term:`user`""",
           "[.. , EXPLISIT_MARKUP_START]",
           "[|end-user|, SUBSTITUTION]",
           "[ , WHITESPACE]",
           "[replace::, DIRECTIVE]",
           "[ :term:`user`, LINE]",
           "[\n, WHITESPACE]",
           "[.. , EXPLISIT_MARKUP_START]",
           "[|PNS ID|, SUBSTITUTION]",
           "[ , WHITESPACE]",
           "[replace::, DIRECTIVE]",
           "[ :term:`user`, LINE]",
           "[\n, WHITESPACE]",
           "[.. , EXPLISIT_MARKUP_START]",
           "[|PNS.ID|, SUBSTITUTION]",
           "[ , WHITESPACE]",
           "[replace::, DIRECTIVE]",
           "[ :term:`user`, LINE]"
           );
  }

  public void testLinks() throws IOException {
    doTest("""
             link_/
             link_!
             "link_"
             'link_'
             link_;
             """,
           "[link_, REFERENCE_NAME]",
           "[/, LINE]",
           "[\n, WHITESPACE]",
           "[link_, REFERENCE_NAME]",
           "[!, LINE]",
           "[\n, WHITESPACE]",
           "[\", LINE]",
           "[link_, REFERENCE_NAME]",
           "[\", LINE]",
           "[\n, WHITESPACE]",
           "[', LINE]",
           "[link_, REFERENCE_NAME]",
           "[', LINE]",
           "[\n, WHITESPACE]",
           "[link_, REFERENCE_NAME]",
           "[;, LINE]",
           "[\n, WHITESPACE]"
           );
  }

  public void testFieldInCodeBlock() throws IOException {
    doTest("""
             .. code-block:: python
                :class: extra-css-class

                 def thing(x):  # comment
                     print("{x} is a thing".format(x=x))""",
           "[.. , EXPLISIT_MARKUP_START]",
           "[code-block::, CUSTOM_DIRECTIVE]",
           "[ , WHITESPACE]",
           "[python\n, LINE]",
           "[ , WHITESPACE]",
           "[  , WHITESPACE]",
           "[:class:, FIELD]",
           "[ extra-css-class, LINE]",
           "[\n, WHITESPACE]",
           "[\n, WHITESPACE]",
           "[    , PYTHON_LINE]",
           "[def thing(x):  # comment, PYTHON_LINE]",
           "[\n, PYTHON_LINE]",
           "[        , PYTHON_LINE]",
           "[print(\"{x} is a thing\".format(x=x)), PYTHON_LINE]"
           );
  }

  public void testInterpreted() throws IOException {
    doTest("""
             :kbd:`1`

             :kbd:`a`

             :kbd:`*`

             :kbd:`12`

             :kbd:`ab`

             :kbd:`**`

             :kbd:`1`, :kbd:`2`""",
           "[:kbd:, FIELD]",
           "[`1`, INTERPRETED]",
           "[\n" +
           ", WHITESPACE]",
           "[\n" +
           ", WHITESPACE]",
           "[:kbd:, FIELD]",
           "[`a`, INTERPRETED]",
           "[\n" +
           ", WHITESPACE]",
           "[\n" +
           ", WHITESPACE]",
           "[:kbd:, FIELD]",
           "[`*`, INTERPRETED]",
           "[\n" +
           ", WHITESPACE]",
           "[\n" +
           ", WHITESPACE]",
           "[:kbd:, FIELD]",
           "[`12`, INTERPRETED]",
           "[\n" +
           ", WHITESPACE]",
           "[\n" +
           ", WHITESPACE]",
           "[:kbd:, FIELD]",
           "[`ab`, INTERPRETED]",
           "[\n" +
           ", WHITESPACE]",
           "[\n" +
           ", WHITESPACE]",
           "[:kbd:, FIELD]",
           "[`**`, INTERPRETED]",
           "[\n" +
           ", WHITESPACE]",
           "[\n" +
           ", WHITESPACE]",
           "[:kbd:, FIELD]",
           "[`1`, INTERPRETED]",
           "[,, LINE]",
           "[ , LINE]",
           "[:kbd:, FIELD]",
           "[`2`, INTERPRETED]"
           );
  }

  private static void doTest(String text, String... expected) throws IOException {
    _RestFlexLexer lexer = createLexer(text);
    for (String expectedTokenText : expected) {
      IElementType type = lexer.advance();
      if (type == null) {
        fail("Not enough tokens");
      }
      String tokenText = "[" + lexer.yytext() + ", " + type + "]";
      assertEquals(expectedTokenText, tokenText);
    }
  }

  public static _RestFlexLexer createLexer(String text) {
    _RestFlexLexer lexer = new _RestFlexLexer(null);
    lexer.reset(text, 0, text.length(), _RestFlexLexer.YYINITIAL);
    return lexer;
  }
}
