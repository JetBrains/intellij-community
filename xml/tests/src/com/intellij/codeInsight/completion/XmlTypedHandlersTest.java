/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 *         Date: 30.08.13
 */
public class XmlTypedHandlersTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testClosingTag() {
    doTest("<foo><<caret>", '/', "<foo></foo>");
  }

  public void testValueQuotesWithMultiCarets() {
    doTest("<foo bar<caret>><foo bar<caret>>", '=', "<foo bar=\"<caret>\"><foo bar=\"<caret>\">");
  }

  public void testValueQuotesWithMultiCaretsMultiline() {
    doTest("<foo bar<caret>\n<foo bar<caret>", '=', "<foo bar=\"<caret>\"\n<foo bar=\"<caret>\"");
  }

  public void testValueQuotesWithMultiCaretsWithDifferentContexts() {
    doTest("<foo bar <caret>><foo bar<caret>>", '=', "<foo bar =<caret>><foo bar=\"<caret>\">");
  }

  public void testCloseTagOnSlashWithMultiCarets() {
    doTest("<bar>\n" +
           "<foo><<caret>\n" +
           "<foo><<caret>\n" +
           "</bar>", '/', "<bar>\n" +
                          "<foo></foo><caret>\n" +
                          "<foo></foo><caret>\n" +
                          "</bar>");
  }

  public void testCloseTagOnGtWithMultiCarets() {
    doTest("<bar>\n" +
           "<foo<caret>\n" +
           "<foo<caret>\n" +
           "</bar>", '>', "<bar>\n" +
                          "<foo><caret></foo>\n" +
                          "<foo><caret></foo>\n" +
                          "</bar>");
  }

  public void _testCloseTagOnSlashWithMultiCaretsInDifferentContexts() {
    doTest("<bar>\n" +
           "<foo><<caret>\n" +
           "<fiz><<caret>\n" +
           "</bar>", '/', "<bar>\n" +
                          "<foo></foo><caret>\n" +
                          "<fiz></fiz><caret>\n" +
                          "</bar>");
  }

  public void _testCloseTagOnGtWithMultiCaretsInDifferentContexts() {
    doTest("<bar>\n" +
           "<foo<caret>\n" +
           "<fiz<caret>\n" +
           "</bar>", '>', "<bar>\n" +
                          "<foo><caret></foo>\n" +
                          "<fiz><caret></fiz>\n" +
                          "</bar>");
  }

  public void testGreedyClosing() {
    doTest("<foo><<caret>foo>", '/', "<foo></foo>");
  }

  public void testValueQuotas() {
    doTest("<foo bar<caret>", '=', "<foo bar=\"<caret>\"");
    WebEditorOptions.getInstance().setInsertQuotesForAttributeValue(false);
    try {
      doTest("<foo bar<caret>", '=', "<foo bar=<caret>");
    }
    finally {
      WebEditorOptions.getInstance().setInsertQuotesForAttributeValue(true);
    }
  }

  private void doTest(String text, char c, String result) {
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    myFixture.type(c);
    myFixture.checkResult(result);
  }
}
