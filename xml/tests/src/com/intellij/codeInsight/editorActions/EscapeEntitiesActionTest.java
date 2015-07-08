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

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Dennis.Ushakov
 */
public class EscapeEntitiesActionTest extends LightCodeInsightFixtureTestCase {
  public void testSimpleHtml() {
    doTest("<<<", "html", "&lt;&lt;&lt;");
  }

  public void testSimpleXml() {
    doTest(">>>", "xml", "&gt;&gt;&gt;");
  }

  public void testWide() {
    doTest("–", "html", "&ndash;");
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

  private void doTest(String text, String extension, String expected) {
    if (!text.contains("<selection>")) {
      text = "<selection>" + text + "</selection>";
    }
    myFixture.configureByText(getTestName(true) + "." + extension, text);
    myFixture.performEditorAction("EscapeEntities");
    myFixture.checkResult(expected);
  }
}
