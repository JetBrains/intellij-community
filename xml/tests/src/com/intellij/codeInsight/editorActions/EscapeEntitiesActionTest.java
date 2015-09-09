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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Dennis.Ushakov
 */
public class EscapeEntitiesActionTest extends LightCodeInsightFixtureTestCase {
  private static final String NDASH = new String(new byte[]{-30, -128, -109}, CharsetToolkit.UTF8_CHARSET);
  private static final String COPY = new String(new byte[]{-62, -82}, CharsetToolkit.UTF8_CHARSET);

  public void testSimpleHtml() {
    doTest("<<<", "html", "&lt;&lt;&lt;");
  }

  public void testSimpleXml() {
    doTest(">>>", "xml", "&gt;&gt;&gt;");
  }

  public void testVeryWide() {
    doTest(NDASH, "html", "&ndash;");
  }

  public void testWide() {
    doTest(COPY, "html", "&reg;");
  }

  public void testAttributeValue() {
    doTest("<a alt='" + NDASH + "'></a>", "html", "<a alt='&ndash;'></a>");
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
    final String finalText = !text.contains("<selection>") ? "<selection>" + text + "</selection>" : text;
    PlatformTestUtil.withEncoding("UTF8", new Runnable() {
      @Override
      public void run() {
        myFixture.configureByText(getTestName(true) + "." + extension, finalText);
        myFixture.performEditorAction("EscapeEntities");
        myFixture.checkResult(expected);
      }
    });
  }
}
