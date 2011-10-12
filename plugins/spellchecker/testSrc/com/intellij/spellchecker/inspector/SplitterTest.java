/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.spellchecker.inspector;

import com.intellij.openapi.util.TextRange;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.inspections.SplitterFactory;
import com.intellij.util.Consumer;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class SplitterTest extends TestCase {


  public void testSplitSimpleCamelCase() {
    String text = "simpleCamelCase";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "simple", "Camel", "Case");
  }

  public void testSplitCamelCaseWithUpperCasedWord() {
    String text = "camelCaseJSP";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "camel", "Case");
  }

  public void testArrays() {
    String text = "Token[]";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "Token");
  }

  public void testIdentifierInSingleQuotes() {
    String text = "'fill'";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "fill");
  }


  public void testWordsInSingleQuotesWithSep() {
    String text = "'test-something'";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "test", "something");
  }

  public void testComplexWordsInQuotes() {
    String text = "\"test-customer's'\"";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "test", "customer's");
  }

  public void testCapitalizedWithShortWords() {
    String text = "IntelliJ";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "Intelli");
  }

  public void testWords() {
    String text = "first-last";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "first", "last");
  }

  public void testCapitalizedWithShortAndLongWords() {
    String text = "IntelliJTestTest";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "Intelli", "Test", "Test");
  }

  public void testWordWithApostrophe1() {
    String text = "don't check";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "don't", "check");
  }

  public void testHexInPlainText() {
    String text = "some text 0xacvfgt";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "some", "text");
  }

  public void testHexInStringLiteral() {
    String text = "qwerty 0x12acfgt test";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "qwerty", "test");
  }


  public void testHex() {
    String text = "0xacvfgt";
    correctListToCheck(SplitterFactory.getInstance().getWordSplitter(), text);
  }

  public void testCheckXmlIgnored() {
    String text = "abcdef" + new String(new char[]{0xDC00}) + "test";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text);
  }

   public void testIdentifiersWithNumbers() {
    String text = "result1";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "result");
  }

  public void testIdentifiersWithNumbersInside() {
    String text = "result1result";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "result","result");
  }

  public void testWordWithApostrophe2() {
    String text = "customers'";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "customers");
  }

  public void testWordWithApostrophe3() {
    String text = "customer's";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "customer's");
  }


  public void testWordWithApostrophe4() {
    String text = "we'll";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "we'll");
  }

  public void testWordWithApostrophe5() {
    String text = "I'm you're we'll";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "you're", "we'll");
  }

  public void testConstantName() {
    String text = "TEST_CONSTANT";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "TEST", "CONSTANT");

  }

  public void testLongConstantName() {
    String text = "TEST_VERY_VERY_LONG_AND_COMPLEX_CONSTANT";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "TEST", "VERY", "VERY", "LONG", "COMPLEX", "CONSTANT");

  }

  public void testJavaComments() {
    String text = "/*special symbols*/";
    correctListToCheck(SplitterFactory.getInstance().getCommentSplitter(), text, "special", "symbols");

  }


  public void testXmlComments() {
    String text = "<!--special symbols-->";
    correctListToCheck(SplitterFactory.getInstance().getCommentSplitter(), text, "special", "symbols");

  }

  public void testCamelCaseInXmlComments() {
    String text = "<!--specialCase symbols-->";
    correctListToCheck(SplitterFactory.getInstance().getCommentSplitter(), text, "special", "Case", "symbols");

  }

  public void testWordsWithNumbers() {
    String text = "testCamelCase123";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "test", "Camel", "Case");

  }

  public void testCommentsWithWordsWithNumbers() {
    String text = "<!--specialCase456 symbols-->";
    correctListToCheck(SplitterFactory.getInstance().getCommentSplitter(), text, "special", "Case", "symbols");

  }

  public void testCommentsWithAbr() {
    String text = "<!--JSPTestClass-->";
    correctListToCheck(SplitterFactory.getInstance().getCommentSplitter(), text, "Test", "Class");

  }

  public void testStringLiterals() {
    String text = "test\ntest\n";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "test", "test");

  }


  public void testCommentWithHtml() {
    String text = "<!--<li>something go here</li> <li>next content</li> foooo barrrr <p> text -->";
    correctListToCheck(SplitterFactory.getInstance().getCommentSplitter(), text, "something", "here", "next", "content", "foooo", "barrrr",
                       "text");

  }

  public void testCommentWithHtmlTagsAndAtr() {
    String text = "<!-- <li style='color:red;'>something go here</li> foooo <li style='color:red;'>barrrr</li> <p> text text -->";
    correctListToCheck(SplitterFactory.getInstance().getCommentSplitter(), text, "something", "here", "foooo", "barrrr", "text", "text");

  }

  public void testSpecial() {
    String text = "test &nbsp; test";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "test", "test");

  }

  public void testColorUC() {
    String text = "#AABBFF";
    correctListToCheck(SplitterFactory.getInstance().getWordSplitter(), text);

  }

  public void testColorUCSC() {
    String text = "#AABBFF;";
    correctListToCheck(SplitterFactory.getInstance().getWordSplitter(), text);

  }

  public void testColorUCSurrounded() {
    String text = "\"#AABBFF\"";
    correctListToCheck(SplitterFactory.getInstance().getWordSplitter(), text);

  }

  public void testColorLC() {
    String text = "#fff";
    correctListToCheck(SplitterFactory.getInstance().getAttributeValueSplitter(), text);

  }

  public void testTooShort() {
    String text = "bgColor carLight";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "Color", "Light");

  }

  public void testPhpVariableCorrectSimple() {
    String text = "$this";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "this");

  }

  public void testPhpVariableCorrect() {
    String text = "$this_this$this";
    correctListToCheck(SplitterFactory.getInstance().getIdentifierSplitter(), text, "this", "this", "this");

  }

  public void testEmail() {
    String text = "some text with email (shkate.test@gmail.com) inside";
    correctListToCheck(SplitterFactory.getInstance().getStringLiteralSplitter(), text, "some", "text", "with", "email", "inside");

  }

  public void testEmailOnly() {
    String text = "shkate123-\u00DC.test@gmail.com";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text);

  }

  public void testUrl() {
    String text = "http://www.jetbrains.com/idea";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text);
  }

  public void testWordBeforeDelimiter() {
    String text = "badd,";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "badd");
  }

  public void testWordAfterDelimiter() {
    String text = ",badd";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "badd");
  }

  public void testWordInCapsBeforeDelimiter() {
    String text = "BADD,";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "BADD");

  }

  public void testWordInCapsAfterDelimiter() {
    String text = ",BADD";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "BADD");

  }

  public void testWordInCapsAfterDelimiter2() {
    String text = "BADD;";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "BADD");

  }

  public void testWordInCapsAfterDelimiter3() {
    String text = ";BADD;";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "BADD");
  }

  public void testWordWithUmlauts() {
    String text = "rechtsb\u00FCndig";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, text);
  }

  public void testWordUpperCasedWithUmlauts() {
    String text = "RECHTSB\u00DCNDIG";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, text);
  }

  public void testCommaSeparatedList() {
    String text = "properties,test,properties";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "properties", "test", "properties");

  }

  public void testSemicolonSeparatedList() {
    String text = "properties;test;properties";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, "properties", "test", "properties");

  }

  public void testProperties1() {
    String text = "properties.test.properties";
    correctListToCheck(SplitterFactory.getInstance().getPropertiesSplitter(), text, "properties", "test", "properties");
  }


  public void testPropertiesWithCamelCase() {
    String text = "upgrade.testCommit.propertiesSomeNews";
    correctListToCheck(SplitterFactory.getInstance().getPropertiesSplitter(), text, "upgrade", "test", "Commit", "properties", "Some",
                       "News");
  }

  public void testWordUpperCasedWithUmlautsInTheBeginning() {
    String text = "\u00DCNDIG";
    correctListToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text, text);
  }


  public void testTCData() {
    final InputStream stream = SplitterTest.class.getResourceAsStream("contents.txt");
    String text = convertStreamToString(stream);
    List<String> words = wordsToCheck(SplitterFactory.getInstance().getPlainTextSplitter(), text);
    assertEquals(0, words.size());
  }


  private static List<String> wordsToCheck(Splitter splitter, final String text) {
    final List<String> words = new ArrayList<String>();
    splitter.split(text, TextRange.allOf(text), new Consumer<TextRange>() {
      @Override
      public void consume(TextRange textRange) {
        words.add(textRange.substring(text));
      }
    });
    return words;
  }


  private static void correctListToCheck(Splitter splitter, String text, @NotNull String... expected) {
    List<String> words = wordsToCheck(splitter, text);
    List<String> expectedWords = Arrays.asList(expected);
    Assert.assertEquals("Splitting:'" + text + "'", expectedWords.toString(), words!=null ? words.toString() : "[]");
  }


  private String convertStreamToString(InputStream is) {
    if (is != null) {
      StringBuilder sb = new StringBuilder();
      String line;

      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        while ((line = reader.readLine()) != null) {
          sb.append(line).append("\n");
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      finally {
        try {
          is.close();
        }
        catch (IOException ignore) {

        }
      }
      return sb.toString();
    }
    else {
      return "";
    }
  }
}
