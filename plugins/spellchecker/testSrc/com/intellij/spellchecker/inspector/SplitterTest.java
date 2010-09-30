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

import com.intellij.spellchecker.inspections.CheckArea;
import com.intellij.spellchecker.inspections.SplitterFactory;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"simple", "Camel", "Case"});
  }

  public void testSplitCamelCaseWithUpperCasedWord() {
    String text = "camelCaseJSP";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"camel", "Case"});
  }

  public void testArrays() {
    String text = "Token[]";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"Token"});
  }

  public void testIdentifierInSingleQuotes() {
    String text = "'fill'";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"fill"});
  }


  public void testWordsInSingleQuotesWithSep() {
    String text = "'test-something'";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "something"});
  }

  public void testComplexWordsInQuotes() {
    String text = "\"test-customer's'\"";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "customer's"});
  }

  public void testCapitalizedWithShortWords() {
    String text = "IntelliJ";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"Intelli"});
  }

  public void testWords() {
    String text = "first-last";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"first", "last"});
  }

  public void testCapitalizedWithShortAndLongWords() {
    String text = "IntelliJTestTest";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"Intelli", "Test", "Test"});
  }

  public void testWordWithApostrophe1() {
    String text = "don't check";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"don't", "check"});
  }

  public void testHexInPlainText() {
    String text = "some text 0xacvfgt";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"some", "text"});
  }

  public void testHexInStringLiteral() {
    String text = "qwerty 0x12acfgt test";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"qwerty", "test"});
  }


  public void testHex() {
    String text = "0xacvfgt";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getWordSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{});
  }

  public void testCheckXmlIgnored() {
    String text = "abcdef" + new String(new char[]{0xDC00}) + "test";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{});
  }

   public void testIdentifiersWithNumbers() {
    String text = "result1";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"result"});
  }

    public void testIdentifiersWithNumbersInside() {
    String text = "result1result";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"result","result"});
  }

  public void testWordWithApostrophe2() {
    String text = "customers'";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"customers"});
  }

  public void testWordWithApostrophe3() {
    String text = "customer's";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"customer's"});
  }


  public void testWordWithApostrophe4() {
    String text = "we'll";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"we'll"});
  }

  public void testWordWithApostrophe5() {
    String text = "I'm you're we'll";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"you're", "we'll"});
  }

  public void testConstantName() {
    String text = "TEST_CONSTANT";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"TEST", "CONSTANT"});

  }

  public void testLongConstantName() {
    String text = "TEST_VERY_VERY_LONG_AND_COMPLEX_CONSTANT";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"TEST", "VERY", "VERY", "LONG", "COMPLEX", "CONSTANT"});

  }

  public void testJavaComments() {
    String text = "/*special symbols*/";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getCommentSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"special", "symbols"});

  }


  public void testXmlComments() {
    String text = "<!--special symbols-->";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getCommentSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"special", "symbols"});

  }

  public void testCamelCaseInXmlComments() {
    String text = "<!--specialCase symbols-->";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getCommentSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"special", "Case", "symbols"});

  }

  public void testWordsWithNumbers() {
    String text = "testCamelCase123";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "Camel", "Case"});

  }

  public void testCommentsWithWordsWithNumbers() {
    String text = "<!--specialCase456 symbols-->";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getCommentSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"special", "Case", "symbols"});

  }

  public void testCommentsWithAbr() {
    String text = "<!--JSPTestClass-->";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getCommentSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"Test", "Class"});

  }

  public void testStringLiterals() {
    String text = "test\ntest\n";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "test"});

  }


  public void testCommentWithHtml() {
    String text = "<!--<li>something go here</li> <li>next content</li> foooo barrrr <p> text -->";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getCommentSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"something", "here", "next", "content", "foooo", "barrrr", "text"});

  }

  public void testCommentWithHtmlTagsAndAtr() {
    String text = "<!-- <li style='color:red;'>something go here</li> foooo <li style='color:red;'>barrrr</li> <p> text text -->";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getCommentSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"something", "here", "foooo", "barrrr", "text", "text"});

  }

  public void testSpecial() {
    String text = "test &nbsp; test";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "test"});

  }

  public void testColorUC() {
    String text = "#AABBFF";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getWordSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{});

  }

  public void testColorUCSC() {
    String text = "#AABBFF;";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getWordSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{});

  }

  public void testColorUCSurrounded() {
    String text = "\"#AABBFF\"";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getWordSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{});

  }

  public void testColorLC() {
    String text = "#fff";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getAttributeValueSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{});

  }

  public void testTooShort() {
    String text = "bgColor carLight";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"Color", "Light"});

  }

  public void testPhpVariableCorrectSimple() {
    String text = "$this";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"this"});

  }

  public void testPhpVariableCorrect() {
    String text = "$this_this$this";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"this", "this", "this"});

  }

  public void testEmail() {
    String text = "some text with email (shkate.test@gmail.com) inside";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getStringLiteralSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"some", "text", "with", "email", "inside"});

  }

  public void testEmailOnly() {
    String text = "shkate123-\u00DC.test@gmail.com";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{});

  }

  public void testUrl() {
    String text = "http://www.jetbrains.com/idea";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{});

  }

  public void testWordBeforeDelimiter() {
    String text = "badd,";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"badd"});

  }

  public void testWordAfterDelimiter() {
    String text = ",badd";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"badd"});

  }

  public void testWordInCapsBeforeDelimiter() {
    String text = "BADD,";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"BADD"});

  }

  public void testWordInCapsAfterDelimiter() {
    String text = ",BADD";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"BADD"});

  }

  public void testWordInCapsAfterDelimiter2() {
    String text = "BADD;";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"BADD"});

  }

  public void testWordInCapsAfterDelimiter3() {
    String text = ";BADD;";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"BADD"});

  }

  public void testWordWithUmlauts() {
    String text = "rechtsb\u00FCndig";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{text});

  }


  public void testWordUpperCasedWithUmlauts() {
    String text = "RECHTSB\u00DCNDIG";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{text});

  }

  public void testCommaSeparatedList() {
    String text = "properties,test,properties";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"properties", "test", "properties"});

  }

  public void testSemicolonSeparatedList() {
    String text = "properties;test;properties";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"properties", "test", "properties"});

  }

  public void testProperties1() {
    String text = "properties.test.properties";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPropertiesSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"properties", "test", "properties"});

  }


  public void testPropertiesWithCamelCase() {
    String text = "upgrade.testCommit.propertiesSomeNews";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPropertiesSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{"upgrade", "test", "Commit", "properties", "Some", "News"});

  }

  public void testWordUpperCasedWithUmlautsInTheBeginning() {
    String text = "\u00DCNDIG";
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    correctListToCheck(checkAreas, text, new String[]{text});

  }


  public void testTCData() {
    final InputStream stream = SplitterTest.class.getResourceAsStream("contents.txt");
    String text = convertStreamToString(stream);
    List<CheckArea> checkAreas = SplitterFactory.getInstance().getPlainTextSplitter().split(text);
    List<String> words = wordsToCheck(checkAreas, text);
    assertNull(words);
  }


  @Nullable
  private static List<String> wordsToCheck(List<CheckArea> toCheck, String text) {
    if (text == null || toCheck == null) return null;
    List<String> words = new ArrayList<String>();
    for (CheckArea area : toCheck) {
      if (!area.isIgnored()) {
        words.add(area.getWord());
      }
    }
    return (words.size() != 0) ? words : null;
  }


  @Nullable
  private static List<String> wordsToIgnore(List<CheckArea> toCheck, String text) {
    if (text == null || toCheck == null) return null;
    List<String> words = new ArrayList<String>();
    for (CheckArea area : toCheck) {
      if (area.isIgnored()) {
        words.add(area.getWord());
      }
    }
    return (words.size() != 0) ? words : null;
  }

  private static void correctListToCheck(List<CheckArea> toCheck, String text, @NotNull String[] expected) {
    List<String> words = wordsToCheck(toCheck, text);
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
