package com.jetbrains.rest;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.rest.lexer._RestFlexLexer;
import junit.framework.TestCase;

import java.io.IOException;
import java.io.Reader;

/**
 * User : catherine
 */
public class RestLexerTest extends TestCase {

  public void testTitle() throws IOException {
    doTest("\n" +
           ".. _quickstart:\n" +
           "\n" +
           "Quick start guide\n" +
           "=================\n",
           "[\n, WHITESPACE]",
           "[.. , EXPLISIT_MARKUP_START]",
           "[_quickstart:, HYPERLINK]",
           "[\n, WHITESPACE]",
           "[\n, WHITESPACE]",
           "[Quick start guide\n" +
           "=================\n" +
           ", TITLE]"
          );
  }

  public void testDirective() throws IOException {
    doTest("\n" +
           ".. note::\n" +
           "    Please",
           "[\n, WHITESPACE]",
           "[.. , EXPLISIT_MARKUP_START]",
            "[note::, DIRECTIVE]",
            "[\n, WHITESPACE]",
            "[    , WHITESPACE]",
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
            "[   , WHITESPACE]",
            "[:width:, FIELD]",
            "[ , LINE]",
            "[300pt, LINE]"
           );
  }

  public void testRole() throws IOException {                //PY-3810
    doTest(" role :roole1:`some text` :notparsed: text\n" +
           ":list field:\n" +
           " :second field:\n",
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
    _RestFlexLexer lexer = new _RestFlexLexer((Reader)null);
    lexer.reset(text, 0, text.length(), _RestFlexLexer.YYINITIAL);
    return lexer;
  }
}
