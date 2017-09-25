/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.formatter;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.util.SystemProperties;

public class XmlFormatterTest extends XmlFormatterTestBase {
  private static final String BASE_PATH = "psi/formatter/xml";

  public void test1() throws Exception {
    doTest();
  }

  public void test2() throws Exception {
    doTest();
  }

  public void test3() throws Exception {
    doTest();
  }

  public void test4() throws Exception {
    doTest();
  }

  public void test5() throws Exception {
    doTest();
  }

  public void test6() throws Exception {
    doTestKeepingWhitespacesStrictly(null);
  }

  public void test7() throws Exception {
    doTestKeepingWhitespacesStrictly(null);
  }

  public void test8() throws Exception {
    final CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.setRightMargin(XMLLanguage.INSTANCE, 32);
    doTestDoNotKeepingWhitespaces();
  }

  public void test9() throws Exception {
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTestDoNotKeepingWhitespaces();
  }

  public void test10() throws Exception {
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_TEXT = true;
    doTestDoNotKeepingWhitespacesKeepingLineBreaks();
  }

  public void test11() throws Exception {
    doTestDoNotWrap("DO_NOT_WRAP", true, 10);
    doTestWrapIfLong("WRAP_IF_LONG", true, 37);
    doChopDownIfLong("CHOP_IF_LONG", true, 37);
    doWrapAlways("WRAP_ALWAYS", true, 140);

    doTestDoNotWrap("ALIGN_DO_NOT_WRAP", false, 10);
    doTestWrapIfLong("ALIGN_WRAP_IF_LONG", false, 37);
    doChopDownIfLong("ALIGN_CHOP_IF_LONG", false, 37);
    doWrapAlways("ALIGN_WRAP_ALWAYS", false, 140);

  }

  public void test12() throws Exception {
    doTest();
  }

  public void test13() throws Exception {
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_KEEP_LINE_BREAKS = true;
    xmlSettings.XML_KEEP_BLANK_LINES = 2;
    doTestDoNotWrap(null, false, 140);
  }

  public void test14() throws Exception {
    doTest();
  }

  public void test15() throws Exception {
    doTest();
  }

  public void test16() throws Exception {
    doTestKeepingWhitespacesStrictly(null);
  }

  public void test17() throws Exception {

    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = true;
    xmlSettings.XML_SPACE_AFTER_TAG_NAME = true;

    try {
      doTest();
    }
    finally {
      xmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = false;
      xmlSettings.XML_SPACE_AFTER_TAG_NAME = false;
    }
  }

  public void testInvalid() throws Exception {
    doTest();
  }

  public void testComment() throws Exception {
    doTest();
  }

  public void testComment2() throws Exception {
    doTest("DO_NOT_KEEP_WHITESPACES");
    doTestKeepingWhitespacesStrictly("KEEP_WHITESPACES");
  }

  public void testComment3() throws Exception {
    doTest("DO_NOT_KEEP_WHITESPACES");
    doTestKeepingWhitespacesStrictly("KEEP_WHITESPACES");
  }

  public void testCDATA() throws Exception {
    doTest();
    doTestDoNotKeepingWhitespaces();
  }

  public void testKeepingSpacesAndWrapping() throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_KEEP_WHITESPACES = true;

    doWrapAlways("ALIGN", true, 120);
    doWrapAlways("DO_NOT_ALIGN", false, 120);
  }

  public void testWhiteSpaceBeforeText() throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_KEEP_WHITESPACES = false;
    doTest();
  }

  public void test18() throws Exception {
    checkFormattingDoesNotProduceException("18");
  }

  public void test19() throws Exception {
    myTextRange = new TextRange(31, 49);
    doTest();
  }

  public void test21() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).setRightMargin(XMLLanguage.INSTANCE, 20);
    doTest();
  }

  public void test22() throws Exception {
    doTest();
  }

  public void testSCR1574() {
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = true;
    doTextTest("<test/>", "<test />");

    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = false;
    doTextTest("<test  />", "<test/>");
  }

  public void testSanity() throws Exception {
    doSanityTest(true);
  }

  public void testSCR1798() {
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = true;
    doTextTest("<a attr=\"value\"/>", "<a attr=\"value\" />");
  }

  public void testIDEA55230() {
    doTextTest("<a b=\"/>","<a b=\"/>");
  }

  public void testEa305257() throws Exception {
    doTest();
  }

  private static long currentFreeMemory() {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    return runtime.freeMemory() + (maxMemory - runtime.totalMemory());
  }

  public void excluded_testStressTest() throws Exception {
    if (!"lesya".equals(SystemProperties.getUserName())) return;
    String name = "stress.xml";
    final PsiFile file = createFile(name, loadFile(name, null));
    long memoryBefore = currentFreeMemory();
    long timeBefore = System.currentTimeMillis();
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> performFormatting(file)), "", "");
    long memoryAfter = currentFreeMemory();
    long timeAfter = System.currentTimeMillis();

    System.out.println("\nMEMORY: " + (memoryAfter - memoryBefore));
    System.out.println("\nTIME: " + (timeAfter - timeBefore));
  }

  private void doWrapAlways(String resultNumber, boolean align, int rightMargin) throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    settings.setRightMargin(XMLLanguage.INSTANCE, rightMargin);
    xmlSettings.XML_ALIGN_ATTRIBUTES = align;
    doTest(resultNumber);
  }

  private void doChopDownIfLong(String resultNumber, boolean align, int rightMargin) throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.setRightMargin(XMLLanguage.INSTANCE, rightMargin);
    xmlSettings.XML_ALIGN_ATTRIBUTES = align;
    doTest(resultNumber);
  }

  private void doTestWrapIfLong(String resultNumber, boolean align, int rightMargin) throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.setRightMargin(XMLLanguage.INSTANCE, rightMargin);
    xmlSettings.XML_ALIGN_ATTRIBUTES = align;
    doTest(resultNumber);
  }

  private void doTestDoNotWrap(String resultNumber, boolean align, int rightMargin) throws Exception {
    CodeStyleSettings settings = getSettings();
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    boolean oldValue = xmlSettings.XML_KEEP_WHITESPACES;
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_KEEP_LINE_BREAKS = true;
    settings.setRightMargin(XMLLanguage.INSTANCE, rightMargin);
    xmlSettings.XML_ALIGN_ATTRIBUTES = align;
    try {
      doTest(resultNumber);
    }
    finally {
      xmlSettings.XML_KEEP_WHITESPACES = oldValue;
    }
  }

  private void doTestDoNotKeepingWhitespacesKeepingLineBreaks() throws Exception {
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    boolean oldValue = xmlSettings.XML_KEEP_WHITESPACES;
    xmlSettings.XML_KEEP_WHITESPACES = false;
    xmlSettings.XML_KEEP_LINE_BREAKS = true;
    try {
      doTest();
    }
    finally {
      xmlSettings.XML_KEEP_WHITESPACES = oldValue;
    }
  }

  private void doTestKeepingWhitespacesStrictly(String s) throws Exception {
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    boolean oldValue = xmlSettings.XML_KEEP_WHITESPACES;
    xmlSettings.XML_KEEP_WHITESPACES = true;
    try {
      doTest(s);
    }
    finally {
      xmlSettings.XML_KEEP_WHITESPACES = oldValue;
    }
  }

  private void doTestDoNotKeepingWhitespaces() throws Exception {
    XmlCodeStyleSettings xmlSettings = getSettings().getCustomSettings(XmlCodeStyleSettings.class);
    boolean oldValue = xmlSettings.XML_KEEP_WHITESPACES;
    xmlSettings.XML_KEEP_WHITESPACES = false;
    xmlSettings.XML_KEEP_LINE_BREAKS = false;
    xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT = false;
    try {
      doTest();
    }
    finally {
      xmlSettings.XML_KEEP_WHITESPACES = oldValue;
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      defaultSettings();
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected String getFileExtension() {
    return "xml";
  }

  public void test20() throws Exception {
    doTest();
  }

  public void testPreserveSpace() throws Exception {
    doTest();
  }

  public void testIdea57965() throws Exception {
    doTest();
  }
  
  public void testIdea52549() {
    doTextTest(
      "<para>\n" +
      "  My <link>link</link> within text.\n" +
      "</para>",
      
      "<para>\n" +
      "    My <link>link</link> within text.\n" +
      "</para>"
    );
  }

  public void testDontKeepLineBreaksInText() {
    final CodeStyleSettings settings = getSettings();
    final XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    settings.setDefaultRightMargin(15);

    settings.HTML_KEEP_LINE_BREAKS_IN_TEXT = false;
    xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT = false;
    doTextTest("<tag>aaa\nbbb\nccc\nddd\n</tag>", "<tag>aaa bbb\n    ccc ddd\n</tag>");

    settings.HTML_TEXT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_TEXT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTextTest("<tag>aaa\nbbb\nccc\nddd\n</tag>", "<tag>aaa bbb ccc ddd\n</tag>");
  }
}
