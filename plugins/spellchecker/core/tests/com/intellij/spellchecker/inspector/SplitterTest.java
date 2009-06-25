package com.intellij.spellchecker.inspector;

import com.intellij.spellchecker.CheckArea;
import com.intellij.spellchecker.Splitter;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class SplitterTest extends TestCase {


  public void testSplitSimpleCamelCase() {
    String text = "simpleCamelCase";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"simple", "Camel", "Case"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testSplitCamelCaseWithUpperCasedWord() {
    String text = "camelCaseJSP";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"camel", "Case"});
    correctIgnored(checkAreas, text, new String[]{"JSP"});
  }

  public void testCapitalizedWithShortWords() {
    String text = "IntelliJ";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{"IntelliJ"});
  }

  public void testCapitalizedWithShortAndLongWords() {
    String text = "IntelliJTestTest";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{});
    correctIgnored(checkAreas, text, new String[]{"IntelliJTestTest"});
  }

  public void testWordWithApostrophe1() {
    String text = "don't check";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"don't", "check"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordWithApostrophe2() {
    String text = "customers'";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"customers"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testWordWithApostrophe3() {
    String text = "customer's";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"customer's"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testConstantName() {
    String text = "TEST_CONSTANT";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"TEST","CONSTANT"});
    correctIgnored(checkAreas, text, new String[]{});
  }

   public void testJavaComments() {
    String text = "/*special symbols*/";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"special","symbols"});
    correctIgnored(checkAreas, text, new String[]{});
  }


  public void testXmlComments() {
   String text = "<!--special symbols-->";
   List<CheckArea> checkAreas = Splitter.splitText(text);
   correctListToCheck(checkAreas, text, new String[]{"special","symbols"});
   correctIgnored(checkAreas, text, new String[]{});
 }

  public void testCamelCaseInXmlComments() {
   String text = "<!--specialCase symbols-->";
   List<CheckArea> checkAreas = Splitter.splitText(text);
   correctListToCheck(checkAreas, text, new String[]{"special","Case","symbols"});
   correctIgnored(checkAreas, text, new String[]{});
 }

  public void testWordsWithNumbers() {
   String text = "testCamelCase123";
   List<CheckArea> checkAreas = Splitter.splitText(text);
   correctListToCheck(checkAreas, text, new String[]{"test","Camel","Case"});
   correctIgnored(checkAreas, text, new String[]{});
 }

  public void testCommentsWithWordsWithNumbers() {
    String text = "<!--specialCase456 symbols-->";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"special","Case","symbols"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testCommentsWithAbr() {
    String text = "<!--JSPTestClass-->";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"Test","Class"});
    correctIgnored(checkAreas, text, new String[]{"JSP"});
  }

  public void testStringLiterals() {
    String text = "test\ntest\n";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "test"});
    correctIgnored(checkAreas, text, new String[]{});
  }

  public void testCommentWithHtml() {
    String text = "<!--<li style='color:red;'>something go here</li> foooo barrrr <p> text text -->";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"something","here","foooo","barrrr","text", "text"});
    correctIgnored(checkAreas, text, new String[]{"go"});
  }

  public void testSpecial() {
    String text = "test &nbsp; test";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"test", "test"});
    correctIgnored(checkAreas, text, new String[]{"&nbsp;"});
  }

  public void testTooShort() {
    String text = "bgColor carLight";
    List<CheckArea> checkAreas = Splitter.splitText(text);
    correctListToCheck(checkAreas, text, new String[]{"Color", "Light"});
    correctIgnored(checkAreas, text, new String[]{"bg","car"});
  }

  public void testComplex() {
   String text = "shkate@gmail.com";
   List<CheckArea> checkAreas = Splitter.splitText(text);
   correctListToCheck(checkAreas, text, new String[]{});
   correctIgnored(checkAreas, text, new String[]{});
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
      Assert.assertEquals( expectedWords,words);
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
      Assert.assertEquals( expectedWords,words);
    }
  }
}
