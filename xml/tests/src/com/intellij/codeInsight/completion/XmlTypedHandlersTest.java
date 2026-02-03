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

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlTypedHandlersTest extends BasePlatformTestCase {
  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      super.runTestRunnable(testRunnable);
      return null;
    });
  }

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
    doTest("""
             <bar>
             <foo><<caret>
             <foo><<caret>
             </bar>""", '/', """
             <bar>
                 <foo></foo><caret>
                 <foo></foo><caret>
             </bar>""");
  }

  public void testCloseTagOnGtWithMultiCarets() {
    doTest("""
             <bar>
             <foo<caret>
             <foo<caret>
             </bar>""", '>', """
             <bar>
             <foo><caret></foo>
             <foo><caret></foo>
             </bar>""");
  }

  public void testCloseTagOnSlashWithMultiCaretsInDifferentContexts() {
    doTest("""
             <bar>
             <foo><<caret>
             <fiz><<caret>
             </bar>""", '/', """
             <bar>
                 <foo></foo><caret>
                 <fiz></fiz><caret>
             </bar>""");
  }

  public void testCloseTagOnGtWithMultiCaretsInDifferentContexts() {
    doTest("""
             <bar>
             <foo<caret>
             <fiz<caret>
             </bar>""", '>', """
             <bar>
             <foo><caret></foo>
             <fiz><caret></fiz>
             </bar>""");
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

  public void testSingleQuotes() {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    final CodeStyleSettings.QuoteStyle quote = settings.HTML_QUOTE_STYLE;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single;
      myFixture.configureByText(HtmlFileType.INSTANCE, "<foo bar<caret>");
      myFixture.type('=');
      myFixture.checkResult("<foo bar='<caret>'");
    } finally {
      settings.HTML_QUOTE_STYLE = quote;
    }
  }

  public void testNoneQuotes() {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    final CodeStyleSettings.QuoteStyle quote = settings.HTML_QUOTE_STYLE;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.None;
      myFixture.configureByText(HtmlFileType.INSTANCE, "<foo bar<caret>>text");
      myFixture.type('=');
      myFixture.checkResult("<foo bar=<caret>>text");
    } finally {
      settings.HTML_QUOTE_STYLE = quote;
    }
  }

  public void testFooBar() {
    doTest("""
             <foo>
               <bar<caret></bar>
             </foo>""",
           '>',
           """
             <foo>
               <bar></bar>
             </foo>""");
  }

  public void testWeb13982() {
    doTest(
      "<a foo=\"1\"\n" +
      "   bar=\"2\"><caret></a>",

      '\n',

      """
        <a foo="1"
           bar="2">
            <caret>
        </a>"""
    );
  }

  public void testWeb392() {
    doTest(
      "<h1>Title</h1>\n" +
      "<p>body text</p><caret>",

      '\n',

      """
        <h1>Title</h1>
        <p>body text</p>
        <caret>"""
    );
  }

  public void testPi() {
    doTest("<<caret>", '?', "<?<caret> ?>");
    doTest("<caret>", '?', "?");
    doTest("<<caret> ?>", '?', "<?<caret> ?>");
  }

  public void testAutoindentEndTag() {
    doTest(
      """
        <div>
            <p>
                Some text
            </p>
            <<caret>""",

      '/',

      """
        <div>
            <p>
                Some text
            </p>
        </div><caret>"""
    );
  }

  public void testSelectionBraces() {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      doTest("<selection><div></div></selection>",
             '(',
             "(<div></div>)");
  }

  public void testSelectionBracesInner() {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      doTest("<div><selection><div></div></selection></div>",
             '(',
             "<div>(<div></div>)</div>");
  }

  public void testSelectionBracesStart() {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      doTest("<selection><div></selection></div>",
             '(',
             "(<div>)</div>");
  }

  public void testSelectionBracesEnd() {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      doTest("<div><selection></div></selection>",
             '(',
             "<div>(</div>)");
  }

  public void testSelectionBracesShort() {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      doTest("<selection><div/></selection>",
             '(',
             "(<div/>)");
  }

  public void testSelectionBracesShortInner() {
      CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      doTest("<div><selection><div/></selection></div>",
             '(',
             "<div>(<div/>)</div>");
  }

  public void testTagClosing() {
    myFixture.configureByText("test.html", "<div><caret></div>");
    myFixture.type("</");
    myFixture.checkResult("<div></></div>");
  }

  public void testTagClosing2() {
    myFixture.configureByText("test.html", "<div><caret></div>");
    myFixture.type("<>aa</");
    myFixture.checkResult("<div><>aa</></div>");
  }

  public void testAttributeValueQuoteEatXml(){
    myFixture.configureByText("test.xml", "<foo attr<caret>><bar attr<caret>></bar></foo>");
    myFixture.type("=\"foo\" a2='");
    myFixture.checkResult("<foo attr=\"foo\" a2=\"'\"><bar attr=\"foo\" a2=\"'\"></bar></foo>");
  }

  public void testAttributeValueQuoteEatHtml(){
    myFixture.configureByText("test.html", "<div attr<caret>><div attr<caret>></div></div>");
    myFixture.type("=\"foo\" a2='bar");
    myFixture.checkResult("<div attr=\"foo\" a2=\"bar\"><div attr=\"foo\" a2=\"bar\"></div></div>");
  }

  private void doTest(String text, char c, String result) {
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    myFixture.type(c);
    myFixture.checkResult(result);
  }

  private HtmlCodeStyleSettings getHtmlSettings() {
    return CodeStyle.getSettings(myFixture.getProject())
                    .getCustomSettings(HtmlCodeStyleSettings.class);
  }
}
