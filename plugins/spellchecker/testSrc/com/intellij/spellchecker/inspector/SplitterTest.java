/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.spellchecker.inspections.*;
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
    correctListToCheck(IdentifierSplitter.getInstance(), text, "simple", "Camel", "Case");
  }

  public void testSplitCamelCaseWithUpperCasedWord() {
    String text = "camelCaseJSP";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "camel", "Case");
  }

  public void testArrays() {
    String text = "Token[]";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "Token");
  }

  public void testIdentifierInSingleQuotes() {
    String text = "'fill'";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "fill");
  }


  public void testWordsInSingleQuotesWithSep() {
    String text = "'test-something'";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "test", "something");
  }

  public void testComplexWordsInQuotes() {
    String text = "\"test-customer's'\"";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "test", "customer's");
  }

  public void testCapitalizedWithShortWords() {
    String text = "IntelliJ";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "Intelli");
  }

  public void testWords() {
    String text = "first-last";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "first", "last");
  }

  public void testCapitalizedWithShortAndLongWords() {
    String text = "IntelliJTestTest";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "Intelli", "Test", "Test");
  }

  public void testWordWithApostrophe1() {
    String text = "don't check";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "don't", "check");
  }

  public void testHexInPlainText() {
    String text = "some text 0xacvfgt";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "some", "text");
  }

  public void testHexInStringLiteral() {
    String text = "qwerty 0x12acfgt test";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "qwerty", "test");
  }


  public void testHex() {
    String text = "0xacvfgt";
    correctListToCheck(WordSplitter.getInstance(), text);
  }

  public void testCheckXmlIgnored() {
    String text = "abcdef" + new String(new char[]{0xDC00}) + "test";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

   public void testIdentifiersWithNumbers() {
    String text = "result1";
     correctListToCheck(IdentifierSplitter.getInstance(), text, "result");
  }

  public void testIdentifiersWithNumbersInside() {
    String text = "result1result";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "result","result");
  }

  public void testWordWithApostrophe2() {
    String text = "customers'";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "customers");
  }

  public void testWordWithApostrophe3() {
    String text = "customer's";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "customer's");
  }


  public void testWordWithApostrophe4() {
    String text = "we'll";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "we'll");
  }

  public void testWordWithApostrophe5() {
    String text = "I'm you're we'll";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "you're", "we'll");
  }

  public void testConstantName() {
    String text = "TEST_CONSTANT";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "TEST", "CONSTANT");

  }

  public void testLongConstantName() {
    String text = "TEST_VERY_VERY_LONG_AND_COMPLEX_CONSTANT";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "TEST", "VERY", "VERY", "LONG", "COMPLEX", "CONSTANT");

  }

  public void testJavaComments() {
    String text = "/*special symbols*/";
    correctListToCheck(CommentSplitter.getInstance(), text, "special", "symbols");

    text = "// comment line which spell check works: misttake";
    correctListToCheck(CommentSplitter.getInstance(), text, "comment", "line", "which", "spell", "check", "works", "misttake");

    text = "// comment line which spell check not works: misttake";
    correctListToCheck(CommentSplitter.getInstance(), text, "comment", "line", "which", "spell", "check", "works", "misttake");

  }


  public void testXmlComments() {
    String text = "<!--special symbols-->";
    correctListToCheck(CommentSplitter.getInstance(), text, "special", "symbols");

  }

  public void testCamelCaseInXmlComments() {
    String text = "<!--specialCase symbols-->";
    correctListToCheck(CommentSplitter.getInstance(), text, "special", "Case", "symbols");

  }

  public void testWordsWithNumbers() {
    String text = "testCamelCase123";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "test", "Camel", "Case");

  }

  public void testCommentsWithWordsWithNumbers() {
    String text = "<!--specialCase456 symbols-->";
    correctListToCheck(CommentSplitter.getInstance(), text, "special", "Case", "symbols");

  }

  public void testCommentsWithAbr() {
    String text = "<!--JSPTestClass-->";
    correctListToCheck(CommentSplitter.getInstance(), text, "Test", "Class");

  }

  public void testStringLiterals() {
    String text = "test\ntest\n";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "test", "test");

  }


  public void testCommentWithHtml() {
    String text = "<!--<li>something go here</li> <li>next content</li> foooo barrrr <p> text -->";
    correctListToCheck(CommentSplitter.getInstance(), text, "something", "here", "next", "content", "foooo", "barrrr",
                       "text");

  }

  public void testCommentWithHtmlTagsAndAtr() {
    String text = "<!-- <li style='color:red;'>something go here</li> foooo <li style='color:red;'>barrrr</li> <p> text text -->";
    correctListToCheck(CommentSplitter.getInstance(), text, "something", "here", "foooo", "barrrr", "text", "text");

  }

  public void testSpecial() {
    String text = "test &nbsp; test";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "test", "test");

  }

  public void testColorUC() {
    String text = "#AABBFF";
    correctListToCheck(WordSplitter.getInstance(), text);

  }

  public void testColorUCSC() {
    String text = "#AABBFF;";
    correctListToCheck(WordSplitter.getInstance(), text);

  }

  public void testColorUCSurrounded() {
    String text = "\"#AABBFF\"";
    correctListToCheck(WordSplitter.getInstance(), text);

  }

  public void testColorLC() {
    String text = "#fff";
    correctListToCheck(TextSplitter.getInstance(), text);

  }

  public void testTooShort() {
    String text = "bgColor carLight";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "Color", "Light");

  }

  public void testPhpVariableCorrectSimple() {
    String text = "$this";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "this");

  }

  public void testPhpVariableCorrect() {
    String text = "$this_this$this";
    correctListToCheck(IdentifierSplitter.getInstance(), text, "this", "this", "this");

  }

  public void testEmail() {
    String text = "some text with email (shkate.test@gmail.com) inside";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "some", "text", "with", "email", "inside");

  }

  public void testEmailOnly() {
    String text = "shkate123-\u00DC.test@gmail.com";
    correctListToCheck(PlainTextSplitter.getInstance(), text);

  }

  public void testUrl() {
    String text = "http://www.jetbrains.com/idea";
    correctListToCheck(PlainTextSplitter.getInstance(), text);
  }

  public void testUrlThenSpaces() {
    String text = "http://www.jetbrains.com/idea asdasdasd sdfsdf";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "asdasdasd", "sdfsdf");
  }

  public void testWordBeforeDelimiter() {
    String text = "badd,";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "badd");
  }

  public void testWordAfterDelimiter() {
    String text = ",badd";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "badd");
  }

  public void testWordInCapsBeforeDelimiter() {
    String text = "BADD,";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "BADD");

  }

  public void testWordInCapsAfterDelimiter() {
    String text = ",BADD";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "BADD");

  }

  public void testWordInCapsAfterDelimiter2() {
    String text = "BADD;";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "BADD");

  }

  public void testWordInCapsAfterDelimiter3() {
    String text = ";BADD;";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "BADD");
  }

  public void testWordWithUmlauts() {
    String text = "rechtsb\u00FCndig";
    correctListToCheck(PlainTextSplitter.getInstance(), text, text);
  }

  public void testWordUpperCasedWithUmlauts() {
    String text = "RECHTSB\u00DCNDIG";
    correctListToCheck(PlainTextSplitter.getInstance(), text, text);
  }

  public void testCommaSeparatedList() {
    String text = "properties,test,properties";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "properties", "test", "properties");

  }

  public void testSemicolonSeparatedList() {
    String text = "properties;test;properties";
    correctListToCheck(PlainTextSplitter.getInstance(), text, "properties", "test", "properties");

  }

  public void testProperties1() {
    String text = "properties.test.properties";
    correctListToCheck(PropertiesSplitter.getInstance(), text, "properties", "test", "properties");
  }


  public void testPropertiesWithCamelCase() {
    String text = "upgrade.testCommit.propertiesSomeNews";
    correctListToCheck(PropertiesSplitter.getInstance(), text, "upgrade", "test", "Commit", "properties", "Some",
                       "News");
  }

  public void testWordUpperCasedWithUmlautsInTheBeginning() {
    String text = "\u00DCNDIG";
    correctListToCheck(PlainTextSplitter.getInstance(), text, text);
  }


  public void testTCData() {
    final InputStream stream = SplitterTest.class.getResourceAsStream("contents.txt");
    String text = convertStreamToString(stream);
    List<String> words = wordsToCheck(PlainTextSplitter.getInstance(), text);
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
