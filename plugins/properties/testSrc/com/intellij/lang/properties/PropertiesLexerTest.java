/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class PropertiesLexerTest extends LightPlatformTestCase {

  private static void doTest(@NonNls String text, @NonNls String[] expectedTokens) {
    Lexer lexer = new PropertiesLexer();
    doTest(text, expectedTokens, lexer);
  }

  private static void doTestHL(@NonNls String text, @NonNls String[] expectedTokens) {
    Lexer lexer = new PropertiesHighlightingLexer();
    doTest(text, expectedTokens, lexer);
  }

  private static void doTest(String text, String[] expectedTokens,Lexer lexer) {
    lexer.start(text);
    int idx = 0;
    while (lexer.getTokenType() != null) {
      if (idx >= expectedTokens.length) fail("Too many tokens");
      String tokenName = lexer.getTokenType().toString();
      String expectedTokenType = expectedTokens[idx++];
      String expectedTokenText = expectedTokens[idx++];
      String tokenText = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
      assertEquals(tokenText, expectedTokenType, tokenName);
      assertEquals("Token type: " + expectedTokenType, expectedTokenText, tokenText);
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }

  public void testSimple() {
    doTest("xxx=yyy", new String[]{
      "Properties:KEY_CHARACTERS", "xxx",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "Properties:VALUE_CHARACTERS", "yyy",
    });
  }

  public void testTwoWords() {
    doTest("xxx=yyy zzz", new String[]{
      "Properties:KEY_CHARACTERS", "xxx",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "Properties:VALUE_CHARACTERS", "yyy zzz",
    });
  }

  public void testMulti() {
    doTest("a  b\n \nx\ty", new String[]{
      "Properties:KEY_CHARACTERS", "a",
      "WHITE_SPACE", "  ",
      "Properties:VALUE_CHARACTERS", "b",
      "WHITE_SPACE", "\n \n",
      "Properties:KEY_CHARACTERS", "x",
      "WHITE_SPACE", "\t",
      "Properties:VALUE_CHARACTERS", "y"
    });
  }

  public void testIncompleteProperty() {
    doTest("a", new String[]{
      "Properties:KEY_CHARACTERS", "a"
    });
  }

  public void testIncompleteProperty2() {
    doTest("a.2=", new String[]{
      "Properties:KEY_CHARACTERS", "a.2",
      "Properties:KEY_VALUE_SEPARATOR", "="
    });
  }

  public void testEscaping() {
    doTest("sdlfkjsd\\l\\\\\\:\\=gk   =   s\\nsssd", new String[]{
      "Properties:KEY_CHARACTERS", "sdlfkjsd\\l\\\\\\:\\=gk",
      "WHITE_SPACE", "   ",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "WHITE_SPACE", "   ",
      "Properties:VALUE_CHARACTERS", "s\\nsssd"
    });
  }

  public void testCRLFEscaping() {
    doTest("sdlfkjsdsssd:a\\\nb", new String[]{
      "Properties:KEY_CHARACTERS", "sdlfkjsdsssd",
      "Properties:KEY_VALUE_SEPARATOR", ":",
      "Properties:VALUE_CHARACTERS", "a\\\nb"
    });
  }

  public void testCRLFEscapingKey() {
    doTest("x\\\ny:z", new String[]{
      "Properties:KEY_CHARACTERS", "x\\\ny",
      "Properties:KEY_VALUE_SEPARATOR", ":",
      "Properties:VALUE_CHARACTERS", "z"
    });
  }

  public void testWhitespace() {
    doTest("x y", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "WHITE_SPACE", " ",
      "Properties:VALUE_CHARACTERS", "y"
    });
  }
  public void testHashInValue() {
    doTest("x=# y", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "Properties:VALUE_CHARACTERS", "# y"
    });
  }
  public void testComments() {
    doTest("#hhhh kkkk \n\n", new String[]{
      "Properties:END_OF_LINE_COMMENT", "#hhhh kkkk ",
      "WHITE_SPACE", "\n\n",
    });
  }
  public void testTabs() {
    doTest("install/htdocs/imcms/html/link_editor.jsp/1002 = URL\\n\\\n" +
           "\t\\t\\teller meta_id:", new String[]{
      "Properties:KEY_CHARACTERS", "install/htdocs/imcms/html/link_editor.jsp/1002",
      "WHITE_SPACE", " ",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "WHITE_SPACE", " ",
      "Properties:VALUE_CHARACTERS", "URL\\n\\\n" + "\t\\t\\teller meta_id:"
    });
  }
  public void testIndentedComments() {
    doTest("   #comm1\n#comm2=n\n\t#comm3", new String[]{
      "WHITE_SPACE", "   ",
      "Properties:END_OF_LINE_COMMENT", "#comm1",
      "WHITE_SPACE", "\n",
      "Properties:END_OF_LINE_COMMENT", "#comm2=n",
      "WHITE_SPACE", "\n\t",
      "Properties:END_OF_LINE_COMMENT", "#comm3",
    });
  }

  public void testHighlighting() {
    doTestHL("x y", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "WHITE_SPACE", " ",
      "Properties:VALUE_CHARACTERS", "y"
    });
  }

  public void testHighlighting2() {
    doTestHL("x\\n\\kz y", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "VALID_STRING_ESCAPE_TOKEN", "\\n",
      "INVALID_CHARACTER_ESCAPE_TOKEN", "\\k",
      "Properties:KEY_CHARACTERS", "z",
      "WHITE_SPACE", " ",
      "Properties:VALUE_CHARACTERS", "y"
    });
  }

  public void testHighlighting3() {
    doTestHL("x  \\uxyzt\\pz\\tp", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "WHITE_SPACE", "  ",
      "INVALID_UNICODE_ESCAPE_TOKEN", "\\uxyzt",
      "INVALID_CHARACTER_ESCAPE_TOKEN", "\\p",
      "Properties:VALUE_CHARACTERS", "z",
      "VALID_STRING_ESCAPE_TOKEN", "\\t",
      "Properties:VALUE_CHARACTERS", "p",
    });
  }
  public void testFramingSpaces() {
    doTestHL("\\ x\\ y\\ =\\ z\\ t\\ \\ ", new String[]{
      "VALID_STRING_ESCAPE_TOKEN", "\\ ",
      "Properties:KEY_CHARACTERS", "x",
      "VALID_STRING_ESCAPE_TOKEN", "\\ ",
      "Properties:KEY_CHARACTERS", "y",
      "VALID_STRING_ESCAPE_TOKEN", "\\ ",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "VALID_STRING_ESCAPE_TOKEN", "\\ ",
      "Properties:VALUE_CHARACTERS", "z",
      "INVALID_CHARACTER_ESCAPE_TOKEN", "\\ ",
      "Properties:VALUE_CHARACTERS", "t",
      "VALID_STRING_ESCAPE_TOKEN", "\\ ",
      "VALID_STRING_ESCAPE_TOKEN", "\\ ",
    });
  }
  public void testSpecialCharsInValue() {
    doTestHL("xxx=\\ x\\ y\\!\\=\\#\\:", new String[]{
      "Properties:KEY_CHARACTERS", "xxx",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "VALID_STRING_ESCAPE_TOKEN", "\\ ",
      "Properties:VALUE_CHARACTERS", "x",
      "INVALID_CHARACTER_ESCAPE_TOKEN", "\\ ",
      "Properties:VALUE_CHARACTERS", "y",
      "VALID_STRING_ESCAPE_TOKEN", "\\!",
      "VALID_STRING_ESCAPE_TOKEN", "\\=",
      "VALID_STRING_ESCAPE_TOKEN", "\\#",
      "VALID_STRING_ESCAPE_TOKEN", "\\:",
    });
  }

}
