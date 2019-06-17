// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.nio.charset.StandardCharsets;

/**
 * @author Dennis.Ushakov
 */
public class EscapeEntitiesActionTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String N_DASH = new String(new byte[]{-30, -128, -109}, StandardCharsets.UTF_8);
  private static final String COPY = new String(new byte[]{-62, -82}, StandardCharsets.UTF_8);

  public void testSimpleHtml() {
    doTest("<<<", "html", "&lt;&lt;&lt;");
  }

  public void testSimpleXml() {
    doTest(">>>", "xml", "&gt;&gt;&gt;");
  }

  public void testVeryWide() {
    doTest(N_DASH, "html", "&ndash;");
  }

  public void testWide() {
    doTest(COPY, "html", "&reg;");
  }

  public void testAttributeValue() {
    doTest("<a alt='" + N_DASH + "'></a>", "html", "<a alt='&ndash;'></a>");
  }

  public void testTag() {
    doTest("<a><</a>", "html", "<a>&lt;</a>");
  }

  public void testXmlStart() {
    doTest("<<<", "xml", "&lt;&lt;&lt;");
  }

  public void testDoctypeSystemPublic() {
    doTest("<!DOCTYPE html\n" +
           "        PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n" +
           "        \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">", "html",
           "<!DOCTYPE html\n" +
           "        PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n" +
           "        \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
  }

  public void testXmlAmp() {
    doTest("<component>\n" +
           "  amp  &    U+0026 (38) XML 1.0 ampersand\n" +
           "</component>", "xml",
           "<component>\n" +
           "  amp  &amp;    U+0026 (38) XML 1.0 ampersand\n" +
           "</component>");
  }

  public void testXmlLt() {
    doTest("<component>\n" +
           "  lt   <    U+003C (60) XML 1.0 less-than sign\n" +
           "</component>", "xml",
           "<component>\n" +
           "  lt   &lt;    U+003C (60) XML 1.0 less-than sign\n" +
           "</component>");
  }

  public void testMultiCaret() {
    doTest("<a><selection><</selection></a>\n" +
           "<a><selection><</selection></a>\n" +
           "<a><selection><</selection></a>\n", "html",
           "<a>&lt;</a>\n" +
           "<a>&lt;</a>\n" +
           "<a>&lt;</a>\n");
  }

  private void doTest(String text, final String extension, final String expected) {
    String finalText = !text.contains("<selection>") ? "<selection>" + text + "</selection>" : text;
    PlatformTestUtil.withEncoding("UTF-8", () -> {
      myFixture.configureByText(getTestName(true) + "." + extension, finalText);
      myFixture.performEditorAction("EscapeEntities");
      myFixture.checkResult(expected);
    });
  }
}