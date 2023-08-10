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
    doTest("""
             <!DOCTYPE html
                     PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                     "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">""", "html",
           """
             <!DOCTYPE html
                     PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                     "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">""");
  }

  public void testXmlAmp() {
    doTest("""
             <component>
               amp  &    U+0026 (38) XML 1.0 ampersand
             </component>""", "xml",
           """
             <component>
               amp  &amp;    U+0026 (38) XML 1.0 ampersand
             </component>""");
  }

  public void testXmlLt() {
    doTest("""
             <component>
               lt   <    U+003C (60) XML 1.0 less-than sign
             </component>""", "xml",
           """
             <component>
               lt   &lt;    U+003C (60) XML 1.0 less-than sign
             </component>""");
  }

  public void testMultiCaret() {
    doTest("""
             <a><selection><</selection></a>
             <a><selection><</selection></a>
             <a><selection><</selection></a>
             """, "html",
           """
             <a>&lt;</a>
             <a>&lt;</a>
             <a>&lt;</a>
             """);
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