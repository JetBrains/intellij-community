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
