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
import com.intellij.spellchecker.inspections.TextSplitter;
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
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"simple", "Camel", "Case"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testSplitCamelCaseWithUpperCasedWord() {
    String text = "camelCaseJSP";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"camel", "Case"});
    correctIgnored(checkAreas, text, new String[]{"JSP"});
  }

  public void testCapitalizedWithShortWords() {
    String text = "IntelliJ";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{"IntelliJ"});
  }

  public void testCapitalizedWithShortAndLongWords() {
    String text = "IntelliJTestTest";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{"IntelliJTestTest"});
  }

  public void testWordWithApostrophe1() {
    String text = "don't check";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"don't", "check"});
    correctIgnored(checkAreas, text, new String[]{});
  }


  public void testWordWithApostrophe2() {
    String text = "customers'";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"customers"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordWithApostrophe3() {
    String text = "customer's";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"customer's"});
    correctIgnored(checkAreas, text, new String[]{});
  }


  public void testWordWithApostrophe4() {
    String text = "we'll";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"we'll"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordWithApostrophe5() {
    String text = "I'm you're we'll";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"you're", "we'll"});
    correctIgnored(checkAreas, text, new String[]{"I'm"});
  }

  public void testConstantName() {
    String text = "TEST_CONSTANT";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"TEST", "CONSTANT"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testLongConstantName() {
    String text = "TEST_VERY_VERY_LONG_AND_COMPLEX_CONSTANT";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"TEST", "VERY", "VERY", "LONG", "COMPLEX", "CONSTANT"});
    correctIgnored(checkAreas, text, new String[]{"AND"});
  }

  public void testJavaComments() {
    String text = "/*special symbols*/";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"special", "symbols"});
    correctIgnored(checkAreas, text, new String[]{});
  }


  public void testXmlComments() {
    String text = "<!--special symbols-->";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"special", "symbols"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testCamelCaseInXmlComments() {
    String text = "<!--specialCase symbols-->";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"special", "Case", "symbols"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordsWithNumbers() {
    String text = "testCamelCase123";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "Camel", "Case"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testCommentsWithWordsWithNumbers() {
    String text = "<!--specialCase456 symbols-->";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"special", "Case", "symbols"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testCommentsWithAbr() {
    String text = "<!--JSPTestClass-->";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"Test", "Class"});
    correctIgnored(checkAreas, text, new String[]{"JSP"});
  }

  public void testStringLiterals() {
    String text = "test\ntest\n";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "test"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testCommentWithHtml() {
    String text = "<!--<li style='color:red;'>something go here</li> foooo barrrr <p> text text -->";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"something", "here", "foooo", "barrrr", "text", "text"});
    correctIgnored(checkAreas, text, new String[]{"go"});
  }

  public void testSpecial() {
    String text = "test &nbsp; test";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "test"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testColorUC() {
    String text = "#AABBFF";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{});
  }

   public void testColorUCSurrounded() {
    String text = "\"#AABBFF\"";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testColorLC() {
    String text = "#fff";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testTooShort() {
    String text = "bgColor carLight";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"Color", "Light"});
    correctIgnored(checkAreas, text, new String[]{"bg", "car"});
  }

  public void testEmail() {
    String text = "shkate.test@gmail.com";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testUrl() {
    String text = "http://www.jetbrains.com/idea";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordBeforeDelimiter() {
    String text = "badd,";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"badd"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordAfterDelimiter() {
    String text = ",badd";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"badd"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordInCapsBeforeDelimiter() {
    String text = "BADD,";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"BADD"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordInCapsAfterDelimiter() {
    String text = ",BADD";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"BADD"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordInCapsAfterDelimiter2() {
    String text = "BADD;";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"BADD"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordInCapsAfterDelimiter3() {
    String text = ";BADD;";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"BADD"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordWithUmlauts() {
    String text = "rechtsb\u00FCndig";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{text});
    correctIgnored(checkAreas, text, new String[]{});
  }


  public void testWordUpperCasedWithUmlauts() {
    String text = "RECHTSB\u00DCNDIG";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{text});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testCommaSeparatedList() {
    String text = "properties,test,properties";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"properties", "test", "properties"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testSemicolonSeparatedList() {
    String text = "properties;test;properties";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"properties", "test", "properties"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testProperties1() {
    String text = "properties.test.properties";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"properties", "test", "properties"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testProperties2() {
    String text = ".properties";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"properties"});
    correctIgnored(checkAreas, text, new String[]{});
  }

   public void testProperties3() {
    String text = "properties.";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"properties"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testPropertiesWithCamelCase() {
    String text = "upgrade.testCommit.propertiesSomeNews";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"upgrade", "test", "Commit", "properties","Some","News"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordUpperCasedWithUmlautsInTheBeginning() {
    String text = "\u00DCNDIG";
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{text});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testNoWordsInHugeBinaryFile() {
    final InputStream stream = SplitterTest.class.getResourceAsStream("crt.txt");

    String text = convertStreamToString(stream);
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
  }

  public void testTCData() {
    final InputStream stream = SplitterTest.class.getResourceAsStream("contents.txt");
    String text = convertStreamToString(stream);
    List<CheckArea> checkAreas = TextSplitter.splitText(text);
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
    if (expected.length == 0) {
      Assert.assertNull(words);
    }
    else {
      Assert.assertNotNull(words);
      Assert.assertEquals(expected.length, words.size());
      List<String> expectedWords = Arrays.asList(expected);
      Assert.assertEquals(expectedWords, words);
    }
  }

  private static void correctIgnored(List<CheckArea> toCheck, String text, @NotNull String[] expected) {
    List<String> words = wordsToIgnore(toCheck, text);
    if (expected.length == 0) {
      Assert.assertNull(words);
    }
    else {
      Assert.assertNotNull(words);
      Assert.assertEquals(expected.length, words.size());
      List<String> expectedWords = Arrays.asList(expected);
      Assert.assertEquals(expectedWords, words);
    }
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
