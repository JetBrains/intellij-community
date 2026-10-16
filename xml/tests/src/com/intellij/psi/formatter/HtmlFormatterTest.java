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

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class HtmlFormatterTest extends XmlFormatterTestBase {
  @Override
  protected String getBasePath() {
    return "psi/formatter/html";
  }

  @Override
  protected String getFileExtension() {
    return "html";
  }

  @Test
  public void test1() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    settings.HTML_KEEP_BLANK_LINES = 0;
    getSettings().setDefaultRightMargin(140);
    settings.HTML_ALIGN_ATTRIBUTES = false;
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.HTML_ALIGN_TEXT = false;
    settings.HTML_KEEP_LINE_BREAKS = false;
    settings.HTML_KEEP_LINE_BREAKS_IN_TEXT = false;
    settings.HTML_KEEP_WHITESPACES = false;

    doTest();
  }

  @Test
  public void test2() throws Exception {
    doTest();
  }

  @Test
  public void test3() throws Exception {
    doTest();
  }

  @Test
  public void test4() throws Exception {
    doTest();
  }

  @Test
  public void test5() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTest();
  }

  @Test
  public void test6() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    getSettings().setDefaultRightMargin(140);
    settings.HTML_ALIGN_ATTRIBUTES = false;
    settings.HTML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.HTML_KEEP_WHITESPACES = false;
    doTest();
  }

  @Test
  public void test7() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    getSettings().setDefaultRightMargin(140);
    settings.HTML_ALIGN_TEXT = false;
    settings.HTML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.HTML_KEEP_LINE_BREAKS = false;
    settings.HTML_KEEP_WHITESPACES = false;
    doTest();
  }

  @Test
  public void test8() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    getSettings().setDefaultRightMargin(140);
    settings.HTML_ALIGN_TEXT = false;
    settings.HTML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.HTML_KEEP_LINE_BREAKS = false;
    settings.HTML_KEEP_WHITESPACES = false;
    doTest();
  }

  @Test
  public void test9() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    getSettings().setDefaultRightMargin(140);
    settings.HTML_ALIGN_TEXT = false;
    settings.HTML_ALIGN_ATTRIBUTES = false;
    settings.HTML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.HTML_KEEP_LINE_BREAKS = false;
    settings.HTML_KEEP_WHITESPACES = false;
    doTest();
  }

  @Test
  public void test10() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    settings.HTML_KEEP_LINE_BREAKS = false;
    getSettings().setDefaultRightMargin(140);
    settings.HTML_ALIGN_ATTRIBUTES = false;
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    settings.HTML_ALIGN_TEXT = false;
    settings.HTML_KEEP_LINE_BREAKS = false;
    settings.HTML_KEEP_WHITESPACES = false;
    doTest();
  }

  @Test
  public void test11() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    settings.HTML_KEEP_LINE_BREAKS = false;
    getSettings().setDefaultRightMargin(140);
    settings.HTML_ALIGN_TEXT = false;
    settings.HTML_KEEP_LINE_BREAKS = false;
    settings.HTML_KEEP_WHITESPACES = false;
    doTest();
  }

  @Test
  public void test12() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    settings.HTML_KEEP_LINE_BREAKS = false;
    getSettings().setDefaultRightMargin(140);
    settings.HTML_ALIGN_TEXT = false;
    settings.HTML_KEEP_LINE_BREAKS = false;
    settings.HTML_KEEP_WHITESPACES = false;
    doTest();
  }

  @Test
  public void test13() {
    getHtmlSettings().HTML_KEEP_LINE_BREAKS = false;
    doTextTest("""
                 <root>
                     <aaa/>
                     <aaa></aaa>
                     <aaa/>
                 </root>""",
               """
                 <root>
                     <aaa/>
                     <aaa></aaa>
                     <aaa/>
                 </root>""");
  }

  @Test
  public void testSpaces() {
    doTextTest("<div> text <div/> text <div> text </div> </div>",
               """
                 <div> text
                     <div/>
                     text
                     <div> text</div>
                 </div>""");
  }

  @Test
  public void testClosingDivOnNextLine() {
    //getSettings().HTML_PLACE_ON_NEW_LINE += ",div";
    doTextTest("<div>ReSharper</div>", "<div>ReSharper</div>");
    doTextTest("<div>Re\nSharper</div>", "<div>Re\n    Sharper\n</div>");
    doTextTest("<div>Re\nSharper </div>", "<div>Re\n    Sharper\n</div>");
  }

  @Test
  public void testLineFeedAfterWrappedTag() {
    doTextTest("<html><body><table></table></body></html>", """
      <html>
      <body>
      <table></table>
      </body>
      </html>""");

    doTextTest("<html><body><table></table><tag></tag></body></html>",
               """
                 <html>
                 <body>
                 <table></table>
                 <tag></tag>
                 </body>
                 </html>""");


    doTextTest("<html><body><table></table> text</body></html>",
               """
                 <html>
                 <body>
                 <table></table>
                 text
                 </body>
                 </html>""");

    doTextTest("<html><body><table></table>text</body></html>",
               """
                 <html>
                 <body>
                 <table></table>
                 text
                 </body>
                 </html>""");

  }

  @Test
  public void testSCR3654() {
    getSettings().setDefaultRightMargin(5);
    getHtmlSettings().HTML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doTextTest("""
                 <html>
                 text textV&aelig;lg JE
                 </html>""", """
                 <html>
                 text
                 textV&aelig;lg
                 JE
                 </html>""");

    getSettings().setDefaultRightMargin(2);

    doTextTest("<html><a>&aelig;</a></html>", """
      <html>
      <a>&aelig;</a>
      </html>""");
  }

  @Test
  public void testBody() throws Exception {
    doTest();
  }

  @Test
  public void testDontAddBreaksInside() throws Exception {
    doTest();
  }

  @Test
  public void testRemoveSpacesBeforeSpanInBody() throws Exception {
    doTest();
  }

  @Test
  public void testH1() throws Exception {
    doTest();
  }

  @Test
  public void testSmall() throws Exception {
    doTest();
  }

  @Test
  public void testTableformatting() throws Exception {
    doTest();
  }

  @Test
  public void testUnknownScriptFormatting() throws Exception  {
    doTest();
  }

  @Test
  public void testHtmlReformatDoesntProduceAssertion() {
    @NonNls String fileText =
      """
        <html>
          <head><title>Simple jsp page</title></head>
          <body>
        <p>Place your co</p>
        <p>ntent here</p>
        </body>
        </html>""";
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("test.html", fileText);
      final XmlTag bodyTag = file.getDocument().getRootTag().getSubTags()[1];
      CodeStyleManager.getInstance(getProject()).reformatRange(
        bodyTag,
        bodyTag.getTextRange().getStartOffset(),
        bodyTag.getTextRange().getEndOffset());
    });
  }

  @Test
  public void testXhtmlReformatDoesntProduceAssertion() {
    @NonNls String fileText =
      """
        <html>
          <head><title>Simple jsp page</title></head>
          <body>
        <p>Place your co</p>
        <p>ntent here</p>
        </body>
        </html>""";
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("test.xhtml", fileText);
      final XmlTag bodyTag = file.getDocument().getRootTag().getSubTags()[1];
      CodeStyleManager.getInstance(getProject()).reformatRange(
        bodyTag,
        bodyTag.getTextRange().getStartOffset(),
        bodyTag.getTextRange().getEndOffset());
    });

  }

  @Test
  public void testInvalidChar() throws Exception {
    doTest();
  }

  @Test
  public void testIndent() throws Exception {
    CommonCodeStyleSettings.IndentOptions htmlIndentOptions = getSettings().getCommonSettings(HTMLLanguage.INSTANCE).getIndentOptions();
    assert  htmlIndentOptions != null : "HTML Indent options not found!";
    int indentSize = htmlIndentOptions.INDENT_SIZE;
    int contIndentSize = htmlIndentOptions.CONTINUATION_INDENT_SIZE;
    try {
      htmlIndentOptions.INDENT_SIZE = 2;
      htmlIndentOptions.CONTINUATION_INDENT_SIZE = 3;
      doTest();
    }
    finally {
      htmlIndentOptions.INDENT_SIZE = indentSize;
      htmlIndentOptions.CONTINUATION_INDENT_SIZE = contIndentSize;
    }
  }

  @Test
  public void testWeb456() {
    doTextTest(
      """
        <html>
        <body>
        <label>
            <textarea>
        This my text which should appear as is
        </textarea>
        </label>
        </body>
        </html>""",

      """
        <html>
        <body>
        <label>
            <textarea>
        This my text which should appear as is
        </textarea>
        </label>
        </body>
        </html>"""
    );
  }

  @Test
  public void testWeb2405() {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    int noIndentMinLines = settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES;
    settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES = 3;
    try {
      doTextTest(
        """
          <table>
          <tr>
              <td>Cell 1.</td>
              <td>Cell 2.</td>
          </tr>
          <tr>
              <td>Cell 3.</td>
          </tr>
          </table>""",

        """
          <table>
          <tr>
          <td>Cell 1.</td>
          <td>Cell 2.</td>
          </tr>
          <tr>
              <td>Cell 3.</td>
          </tr>
          </table>"""
      );
    }
    finally {
      settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES = noIndentMinLines;
    }
  }

  @Test
  public void testWeb12840() {
    doTextTest(
      """
        <div\s
        id="some"
        class="some"
        >""",

      """
        <div
                id="some"
                class="some"
        >"""
    );
  }

  @Test
  public void testWeb16223() {
    doTextTest(
      """
        <img
        id="image-id"
        className="thumbnail"
        src="/some/path/to/the/images/image-name.png"
        alt='image'
        />""",

      """
        <img
                id="image-id"
                className="thumbnail"
                src="/some/path/to/the/images/image-name.png"
                alt='image'
        />"""
    );
  }

  @Test
  public void testWeb12937() {
    doTextTest(
      """
        <div id="top"></div><!-- /#top -->
        <div id="nav">
        <div id="logo"></div><!-- /#logo -->
        </div>""",

      """
        <div id="top"></div><!-- /#top -->
        <div id="nav">
            <div id="logo"></div><!-- /#logo -->
        </div>"""
    );
  }

  @Test
  public void test10809() {
    doTextTest(
      """
        <p>foobar</p>
        <div>foobar</div>
        <p>foobar</p>
        <div>foobar</div>
        <div>foobar</div>
        <p>foobar</p>
        <p>foobar</p>
        <div>
            <p>foobar</p>
            <div>foobar</div>
            <p>foobar</p>
            <div>foobar</div>
            <div>foobar</div>
            <p>foobar</p>
            <p>foobar</p>
        </div>""",

      """
        <p>foobar</p>
        <div>foobar</div>
        <p>foobar</p>
        <div>foobar</div>
        <div>foobar</div>
        <p>foobar</p>
        <p>foobar</p>
        <div>
            <p>foobar</p>
            <div>foobar</div>
            <p>foobar</p>
            <div>foobar</div>
            <div>foobar</div>
            <p>foobar</p>
            <p>foobar</p>
        </div>"""
    );
  }

  @Test
  public void testWeb18909() {
    doTextTest("""
                 <!doctype html>
                 <html>
                 <body>
                 <section>
                     <pre><code class="language-javascript">function test(i) {
                     if (i===1) {
                         console.log('output');
                     }
                 }</code></pre>
                 </section>
                 </body>
                 </html>""",
               """
                 <!doctype html>
                 <html>
                 <body>
                 <section>
                     <pre><code class="language-javascript">function test(i) {
                     if (i===1) {
                         console.log('output');
                     }
                 }</code></pre>
                 </section>
                 </body>
                 </html>""");
  }

  @Test
  public void testSingleQuotes() throws Exception {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    CodeStyleSettings.QuoteStyle quoteStyle = settings.HTML_QUOTE_STYLE;
    boolean enforce = settings.HTML_ENFORCE_QUOTES;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single;
      settings.HTML_ENFORCE_QUOTES = true;
      doTest();
    }
    finally {
      settings.HTML_QUOTE_STYLE = quoteStyle;
      settings.HTML_ENFORCE_QUOTES = enforce;
    }
  }

  @Test
  public void testWeb18213() {
    doTextTest(
      """
        <div class="s">
            <span class="loading"></span>
                <span>
                Loading...
                </span>
        </div>""",

      """
        <div class="s">
            <span class="loading"></span>
            <span>
                Loading...
                </span>
        </div>"""
    );
  }

  @Test
  public void testSpaceInEmptyTag() {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    settings.HTML_SPACE_INSIDE_EMPTY_TAG = true;
    doTextTest("""
                 <div class="emptyWithAttributes"/>
                 <div/>
                 <div class="notEmpty"></div>""",
               """
                 <div class="emptyWithAttributes" />
                 <div />
                 <div class="notEmpty"></div>""");
  }

  @Test
  public void testMultilineTags_NewlinesBeforeAndAfterAttributes() {
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    settings.HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    settings.HTML_NEWLINE_AFTER_LAST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    String source = """
      <div class="singleline" foo="1" bar="2"/>
      <div class="singleline"></div>
      <div class="multiline" foo="1"
                bar="2"></div>
      <div class="selfClosingMultiline" foo="1" bar="2"
      />
      <!--void tags-->
      <input type="button" value="Ok">
      <br>
      """;
    doTextTest(
      source,
      """
        <div class="singleline" foo="1" bar="2"/>
        <div class="singleline"></div>
        <div
                class="multiline" foo="1"
                bar="2"
        ></div>
        <div
                class="selfClosingMultiline" foo="1" bar="2"
        />
        <!--void tags-->
        <input type="button" value="Ok">
        <br>
        """);
    settings.HTML_SPACE_INSIDE_EMPTY_TAG = true;
    doTextTest(
      source,
      """
        <div class="singleline" foo="1" bar="2" />
        <div class="singleline"></div>
        <div
                class="multiline" foo="1"
                bar="2"
        ></div>
        <div
                class="selfClosingMultiline" foo="1" bar="2"
        />
        <!--void tags-->
        <input type="button" value="Ok">
        <br>
        """);
  }

  @Test
  public void testWeb434() {
    doTextTest("""
                 <div>
                     <img></div>""",
               """
                 <div>
                     <img>
                 </div>""");
    doTextTest("""
                 <div>
                     text<br></div>""",
               """
                 <div>
                     text<br>
                 </div>""");
    doTextTest("<div><img></div>", "<div><img></div>");
    doTextTest("<div>text<br>text</div>", "<div>text<br>text</div>");
  }

  @Test
  public void testWeb434NoLineBreakAfterStartTag() {
    doTextTest("""
                 <div><img>
                     <img></div>""",
               """
                 <div><img>
                     <img></div>""");
    doTextTest("""
                 <table>
                     <tr>
                         <td><select name="a">
                             <option>a</option>
                         </select></td>
                         <td><input type="text" id="b"
                                    name="b"/></td>
                     </tr>
                 </table>""",
               """
                 <table>
                     <tr>
                         <td><select name="a">
                             <option>a</option>
                         </select></td>
                         <td><input type="text" id="b"
                                    name="b"/></td>
                     </tr>
                 </table>""");
  }

  @Test
  public void testWeb434LineBreakAddedAfterStartTag() {
    doTextTest("<div><div>a</div><img></div>",
               """
                 <div>
                     <div>a</div>
                     <img>
                 </div>""");
  }

  @Test
  public void testWeb434AttributesOnManyLines() {
    doTextTest("""
                 <div class="a"
                      id="b"><img></div>""",
               """
                 <div class="a"
                      id="b"><img></div>""");
  }

  @Test
  public void testWeb434DontAddBreaksIfInlineContent() {
    doTextTest("""
                 <p>
                     text <b>bold</b></p>""",
               """
                 <p>
                     text <b>bold</b></p>""");
  }

  @Test
  public void testWeb434KeepWhitespacesInside() {
    doTextTest("""
                 <pre>
                     <b>bold</b></pre>""",
               """
                 <pre>
                     <b>bold</b></pre>""");
  }

  @Test
  public void testWeb10625() {
    doTextTest("""
                 <html>
                 <body>
                 <form action="foo">
                     <input name="huhu"/>   </form>
                 <a href="google.com"> <img src="logo.gif"> </a>

                 <div><span></span></div>
                 <ul>
                     <li>
                         <a href="#">dddd</a>   </li>
                 </ul>
                 </body>
                 </html>""",
               """
                 <html>
                 <body>
                 <form action="foo">
                     <input name="huhu"/>
                 </form>
                 <a href="google.com"> <img src="logo.gif"> </a>

                 <div><span></span></div>
                 <ul>
                     <li>
                         <a href="#">dddd</a>
                     </li>
                 </ul>
                 </body>
                 </html>""");
  }

  @Test
  public void testWeb33900() {
    doTextTest("""
                 <div class="main-content no-name">
                     <div class="card signin-card pre-shift no-name">
                         <img class="circle-mask" src="avatar_2x.png"></div>
                 </div>""",
               """
                 <div class="main-content no-name">
                     <div class="card signin-card pre-shift no-name">
                         <img class="circle-mask" src="avatar_2x.png">
                     </div>
                 </div>""");
  }

  @Test
  public void testWeb44449() {
    doTextTest("""
                 <input value=1000 />
                 <input value=1000   />
                 <input value="1000" />
                 <input disabled />""",
               """
                 <input value=1000 />
                 <input value=1000 />
                 <input value="1000"/>
                 <input disabled/>""");
  }

  @Test
  public void testWeb44449NewLineAfterLastAttribute() {
    getHtmlSettings().HTML_NEWLINE_AFTER_LAST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    doTextTest("""
                 <input value=1000 />
                 <input value="1000" />""",
               """
                 <input value=1000 />
                 <input value="1000"/>""");
  }

  @Test
  public void testWeb77174DefaultTagListsHaveNoDuplicates() {
    HtmlCodeStyleSettings settings = getHtmlSettings();
    for (String tagList : List.of(settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE,
                                  settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE,
                                  settings.HTML_DO_NOT_INDENT_CHILDREN_OF,
                                  settings.HTML_KEEP_WHITESPACES_INSIDE,
                                  settings.HTML_INLINE_ELEMENTS,
                                  settings.HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT)) {
      List<String> names = List.of(tagList.split(","));
      assertEquals(new HashSet<>(names).size(), names.size(), tagList);
    }
  }

  @Test
  public void testWeb59303() {
    HtmlCodeStyleSettings settings = getHtmlSettings();
    getSettings().setDefaultRightMargin(40);
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    settings.HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    settings.HTML_NEWLINE_AFTER_LAST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    doTextTest("""
                 <h1 id="very-long-attribute-that-would-exceed-the-max-column-width">
                     Heading
                 </h1>
                 <h1 id="short">
                     Heading
                 </h1>""",
               """
                 <h1
                         id="very-long-attribute-that-would-exceed-the-max-column-width"
                 >
                     Heading
                 </h1>
                 <h1 id="short">
                     Heading
                 </h1>""");
  }

  @Test
  public void testWeb59303FirstAttributeStaysWithoutNewLineBeforeIt() {
    HtmlCodeStyleSettings settings = getHtmlSettings();
    getSettings().setDefaultRightMargin(40);
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    doTextTest("""
                 <h1 id="very-long-attribute-that-would-exceed-the-max-column-width">
                     Heading
                 </h1>""",
               """
                 <h1 id="very-long-attribute-that-would-exceed-the-max-column-width">
                     Heading
                 </h1>""");
  }

  @Test
  public void testWeb59303WrapAlwaysKeepsSingleAttribute() {
    HtmlCodeStyleSettings settings = getHtmlSettings();
    settings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    settings.HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    settings.HTML_NEWLINE_AFTER_LAST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.WhenMultiline;
    doTextTest("""
                 <div class="a"></div>
                 <div class="a" id="b"></div>""",
               """
                 <div class="a"></div>
                 <div
                         class="a"
                         id="b"
                 ></div>""");
  }

  @NotNull
  private HtmlCodeStyleSettings getHtmlSettings() {
    return CodeStyle.getSettings(getProject()).getCustomSettings(HtmlCodeStyleSettings.class);
  }
}
