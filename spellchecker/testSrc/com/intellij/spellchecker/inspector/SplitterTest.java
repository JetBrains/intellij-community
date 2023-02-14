// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspector;

import com.intellij.openapi.util.TextRange;
import com.intellij.spellchecker.inspections.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("SpellCheckingInspection")
public class SplitterTest {
  @Test
  public void testSplitSimpleCamelCase() {
    String text = "simpleCamelCase";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "simple", "Camel", "Case");
  }

  @Test
  public void testSplitCamelCaseWithUpperCasedWord() {
    String text = "camelCaseJSP";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "camel", "Case");
  }

  @Test
  public void testArrays() {
    String text = "Token[]";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "Token");
  }

  @Test
  public void testIdentifierInSingleQuotes() {
    String text = "'fill'";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "fill");
  }

  @Test
  public void testWordsInSingleQuotesWithSep() {
    String text = "'test-something'";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "test", "something");
  }

  @Test
  public void testComplexWordsInQuotes() {
    String text = "\"test-customer's'\"";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "test", "customer's");
  }

  @Test
  public void testCapitalizedWithShortWords() {
    String text = "IntelliJ";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "Intelli");
  }

  @Test
  public void testWords() {
    String text = "first-last";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "first", "last");
  }

  @Test
  public void testCapitalizedWithShortAndLongWords() {
    String text = "IntelliJTestTest";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "Intelli", "Test", "Test");
  }

  @Test
  public void testWordWithApostrophe1() {
    String text = "don't check";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "don't", "check");
  }

  @Test
  public void testHexInPlainText() {
    String text = "some text 0xacvfgt";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "some", "text");
  }

  @Test
  public void testHexInStringLiteral() {
    String text = "qwerty 0x12acfgt test";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "qwerty", "test");
  }

  @Test
  public void testHex() {
    String text = "0xacvfgt";
    correctListToCheck(WordSplitter.getInstance(), text);
  }

  @Test
  public void testCheckXmlIgnored() {
    String text = "abcdef" + new String(new char[]{0xDC00}) + "test";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testIdentifiersWithNumbers() {
    String text = "result1";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "result");
  }

  @Test
  public void testIdentifiersWithNumbersInside() {
    String text = "result1result";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "result", "result");
  }

  @Test
  public void testWordWithApostrophe2() {
    String text = "customers'";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "customers");
  }

  @Test
  public void testWordWithApostrophe3() {
    String text = "customer's";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "customer's");
  }

  @Test
  public void testWordWithApostrophe4() {
    String text = "we'll";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "we'll");
  }

  @Test
  public void testWordWithApostrophe5() {
    String text = "I'm you're we'll";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "you're", "we'll");
  }

  @Test
  public void testUnicodeCombiningChars() {
    correctListToCheck(PlainTextSplitter.getInstance(), "бо́льшую", "бо́льшую");
    correctListToCheck(PlainTextSplitter.getInstance(), "dafür", "dafür");
  }

  @Test
  public void testConstantName() {
    String text = "TEST_CONSTANT";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "TEST", "CONSTANT");
  }

  @Test
  public void testLongConstantName() {
    String text = "TEST_VERY_VERY_LONG_AND_COMPLEX_CONSTANT";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "TEST", "VERY", "VERY", "LONG", "COMPLEX", "CONSTANT");
  }

  @Test
  public void testJavaComments() {
    String text = "/*special symbols*/";
    correctListToCheck(CommentSplitter.getInstance(), text, "special", "symbols");

    text = "// comment line which spell check works: misttake";
    correctListToCheck(CommentSplitter.getInstance(), text, "comment", "line", "which", "spell", "check", "works", "misttake");

    text = "// comment line which spell check not works: misttake";
    correctListToCheck(CommentSplitter.getInstance(), text, "comment", "line", "which", "spell", "check", "works", "misttake");
  }

  @Test
  public void testXmlComments() {
    String text = "<!--special symbols-->";
    correctListToCheck(CommentSplitter.getInstance(), text, "special", "symbols");
  }

  @Test
  public void testCamelCaseInXmlComments() {
    String text = "<!--specialCase symbols-->";
    correctListToCheck(CommentSplitter.getInstance(), text, "special", "Case", "symbols");
  }

  @Test
  public void testWordsWithNumbers() {
    String text = "testCamelCase123";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "test", "Camel", "Case");
  }

  @Test
  public void testCommentsWithWordsWithNumbers() {
    String text = "<!--specialCase456 symbols-->";
    correctListToCheck(CommentSplitter.getInstance(), text, "special", "Case", "symbols");
  }

  @Test
  public void testCommentsWithAbr() {
    String text = "<!--JSPTestClass-->";
    correctListToCheck(CommentSplitter.getInstance(), text, "Test", "Class");
  }

  @Test
  public void testStringLiterals() {
    String text = "test\ntest\n";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "test", "test");
  }

  @Test
  public void testCommentWithHtml() {
    String text = "<!--<li>something go here</li> <li>next content</li> foooo barrrr <p> text -->";
    correctListToCheck(CommentSplitter.getInstance(), text, "something", "here", "next", "content", "foooo", "barrrr", "text");
  }

  @Test
  public void testCommentWithHtmlTagsAndAtr() {
    String text = "<!-- <li style='color:red;'>something go here</li> foooo <li style='color:red;'>barrrr</li> <p> text text -->";
    correctListToCheck(CommentSplitter.getInstance(), text, "something", "here", "foooo", "barrrr", "text", "text");
  }

  @Test
  public void testSpecial() {
    String text = "test &nbsp; test &sup; &gt;";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "test", "test");
  }

  @Test
  public void testColorUC() {
    String text = "#AABBFF";
    correctListToCheck(WordSplitter.getInstance(), text);
  }

  @Test
  public void testColorUCSC() {
    String text = "#AABBFF;";
    correctListToCheck(WordSplitter.getInstance(), text);
  }

  @Test
  public void testColorUCSurrounded() {
    String text = "\"#AABBFF\"";
    correctListToCheck(WordSplitter.getInstance(), text);
  }

  @Test
  public void testColorLC() {
    String text = "#fff";
    correctListToCheck(TextSplitter.getInstance(), text);
  }

  @Test
  public void testTooShort() {
    String text = "bgColor carLight";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "Color", "Light");
  }

  @Test
  public void testPhpVariableCorrectSimple() {
    String text = "$this";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "this");
  }

  @Test
  public void testPhpVariableCorrect() {
    String text = "$this_this$this";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "this", "this", "this");
  }

  @Test
  public void testEmail() {
    String text = "some text with email (shkate.test@gmail.com) inside";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "some", "text", "with", "email", "inside");
  }

  @Test
  public void testEmailOnly() {
    String text = "shkate123-\u00DC.test@gmail.com";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testEmailWithPlus() {
    String text = "test+test@gmail.com";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testEmailNoDLT() {
    String text = "user@localserver";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testUrl() {
    String text = "https://www.jetbrains.com/idea";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testUrlThenSpaces() {
    String text = "https://www.jetbrains.com/idea asdasdasd sdfsdf";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "asdasdasd", "sdfsdf");
  }

  @Test
  public void testShortUrl() {
    String text = "https://test.com";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testUrlWithFragmentID() {
    String text = "http://www.example.org/foo.html#bar";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testUrlWithQuery() {
    String text = "http://example.com/over/there?name=ferret";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testEncodedUrl() {
    String text = "http://www.test.com/test/example.html?var=This+is+a+simple+%26+short+test";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testUUID() {
    String text = "cc27bbbc-d763-44b5-95fd-46124b6e84ca";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  @Test
  public void testUUIDInsideText() {
    String text = "asdasd ee3aaabc-98cb-47cf-a732-00f55f65975d asdasd";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "asdasd", "asdasd");
  }

  @Test
  public void testWordBeforeDelimiter() {
    String text = "badd,";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "badd");
  }

  @Test
  public void testWordAfterDelimiter() {
    String text = ",badd";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "badd");
  }

  @Test
  public void testWordInCapsBeforeDelimiter() {
    String text = "BADD,";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "BADD");
  }

  @Test
  public void testWordInCapsAfterDelimiter() {
    String text = ",BADD";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "BADD");
  }

  @Test
  public void testWordInCapsAfterDelimiter2() {
    String text = "BADD;";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "BADD");
  }

  @Test
  public void testWordInCapsAfterDelimiter3() {
    String text = ";BADD;";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "BADD");
  }

  @Test
  public void testWordWithUmlauts() {
    String text = "rechtsb\u00FCndig";
    correctListToCheck(PlainTextSplitter.getInstance(), text, text);
  }

  @Test
  public void testWordUpperCasedWithUmlauts() {
    String text = "RECHTSB\u00DCNDIG";
    correctListToCheck(PlainTextSplitter.getInstance(), text, text);
  }

  @Test
  public void testCommaSeparatedList() {
    String text = "properties,test,properties";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "properties", "test", "properties");
  }

  @Test
  public void testSemicolonSeparatedList() {
    String text = "properties;test;properties";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "properties", "test", "properties");
  }

  @Test
  public void testProperties1() {
    String text = "properties.test.properties";
    correctListToCheck(PropertiesSplitter.getInstance(), text, "properties", "test", "properties");
  }

  @Test
  public void testPropertiesWithCamelCase() {
    String text = "upgrade.testCommit.propertiesSomeNews";
    correctListToCheck(PropertiesSplitter.getInstance(), text, "upgrade", "test", "Commit", "properties", "Some", "News");
  }

  @Test
  public void testWordUpperCasedWithUmlautsInTheBeginning() {
    String text = "\u00DCNDIG";
    correctListToCheck(PlainTextSplitter.getInstance(), text, text);
  }

  @Test
  public void testTCData() {
    final InputStream stream = SplitterTest.class.getResourceAsStream("contents.txt");
    String text = convertStreamToString(stream);
    List<String> words = wordsToCheck(PlainTextSplitter.getInstance(), text);
    assertEquals(0, words.size());
  }

  @Test
  public void testMd5InsideText() {
    String text = "asdasd 79054025255fb1a26e4bc422adfebeed asdasd";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "asdasd", "asdasd");
  }

  @Test
  public void testSha1InsideText() {
    String text = "asdasd c3499c2729730aaff07efb8676a92dcb6f8a3f8f asdasd";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "asdasd", "asdasd");
  }

  @Test
  public void testSha256InsideText() {
    String text = "asdasd 50d858e0985ecc7f60418aaf0cc5ab587f42c2570a884095a9e8ccacd0f6545c asdasd";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "asdasd", "asdasd");
  }

  @Test
  public void testJwtInsideText() {
    String text = "asdasd eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.dyt0CoTl4WoVjAHI9Q_CwSKhl6d_9rhM3NrXuJttkao asdasd";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "asdasd", "asdasd");
  }

  @NotNull
  private static List<String> wordsToCheck(Splitter splitter, final String text) {
    final List<String> words = new ArrayList<>();
    splitter.split(text, TextRange.allOf(text), textRange -> words.add(textRange.substring(text)));
    return words;
  }

  private static void correctListToCheck(Splitter splitter, String text, String @NotNull ... expected) {
    List<String> words = wordsToCheck(splitter, text);
    List<String> expectedWords = Arrays.asList(expected);
    assertEquals("Splitting:'" + text + "'", expectedWords.toString(), words.toString());
  }

  private static String convertStreamToString(InputStream is) {
    if (is != null) {
      StringBuilder sb = new StringBuilder();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line).append('\n');
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      return sb.toString();
    }
    else {
      return "";
    }
  }
}
