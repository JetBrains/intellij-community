// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.google.common.base.Joiner;
import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.codeInsight.template.HtmlTextContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownBooleanAttributeInspection;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.xml.util.HtmlUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Tests from https://github.com/emmetio/emmet/blob/master/test/expandAbbreviation.js
 * and from https://github.com/emmetio/emmet/blob/master/lib/snippets.json
 *
 * The new one: https://github.com/emmetio/snippets/blob/047c644a3d29f75f7cbb8183ebb91ab3fbcd143b/html.json
 */
public class HtmlEmmetAbbreviationTest extends EmmetAbbreviationTestSuite {

  public HtmlEmmetAbbreviationTest() throws IOException {
    addHtmlAbbreviationTests();
    addPlusOperatorTests();
    addGreaterThanOperatorTests();
    addAttributesOperatorTests();
    addExpandosTests();
    addCounterTests();
    addOtherTests();
    addGroupTests();
    addGroupMultiplicationTests();
    addImpliedTagNameTests();
    addTextNodesTests();
    addClimbUpOperatorTests();
    addRegressionTests();
    addMultiCaretTests();
    addDefaultAttributesTests();
    addBooleanAttributesTests();
    addQuotesTests();
    addEndVariableTests();
  }

  @Override
  protected void setUp(@NotNull Project project) throws Exception {
    super.setUp(project);    
    final TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(project);
    TemplateContextType contextType = TemplateContextTypes.getByClass(HtmlTextContextType.class);

    final Template al = templateManager.createTemplate("al", "testing", "<a !href=\"http://|\"></a>");
    ((TemplateImpl)al).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(al, project);

    final Template al2 = templateManager.createTemplate("al2", "testing", "<a !href></a>");
    ((TemplateImpl)al2).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(al2, project);
  }

  public static Test suite() throws IOException {
    return new HtmlEmmetAbbreviationTest();
  }

  /**
   * Testing all abbreviations from https://github.com/emmetio/emmet/blob/master/snippets.json
   */
  private void addHtmlAbbreviationTests() throws IOException {
    addTestFromJson(getTestDataPath() + "/html.abbreviation.json", "html");
  }

  private void addMultiCaretTests() {
    addTest("""
              div<caret>
              div<caret>
              div<caret>""", """
              <div><caret></div>
              <div><caret></div>
              <div><caret></div>""");
    //should place caret at first variable
    addTest("""
              a<caret>
              a<caret>
              a<caret>""", """
              <a href="<caret>"></a>
              <a href="<caret>"></a>
              <a href="<caret>"></a>""");
  }

  private void addRegressionTests() {
    addTest("ul>li[@click=\"method\" :title=\"task.title\"]", """
      <ul>
          <li @click="method" :title="task.title"></li>
      </ul>""");
    addTest("t[type=application/atom+xml]", "<t type=\"application/atom+xml\"></t>");
    addTest("basefont[href]/", "<basefont href=\"\">");
    addTest("use[xlink:href]", "<use xlink:href=\"\"></use>", "xml");
    addTest("input[-title]", "<input type=\"text\" -title=\"\">");
    addTest("input[+title]", "<input type=\"text\" +title=\"\">");
    addTest("<br/>br", "<br/><br>");
    addTest("div[0]", "div[0]");
    addTest("div{&nbsp;}", "<div>&nbsp;</div>");
    addTest("div[data-object=]", "<div data-object=\"\"></div>");
    addTest("div[data-object={id:2}]", "<div data-object=\"{id:2}\"></div>");
    addTest("ul>li(a)*2", """
      <ul>
          <li><a href=""></a></li>
          <li><a href=""></a></li>
      </ul>""");
    addTest("ul>li(a)", """
      <ul>
          <li><a href=""></a></li>
      </ul>""");

    addTest("(>span)", "<span></span>");
    addTest("div(span)", "<div><span></span></div>");
    addTest("div(span)+div", "<div><span></span></div>\n" +
                             "<div></div>");
    addTest("div(>span)+div", "<div><span></span></div>\n" +
                              "<div></div>");
    addTest("div.recording>h2.recording__title(>span)+div.recording__image(>p.recording__meta)", """
      <div class="recording">
          <h2 class="recording__title"><span></span></h2>
          <div class="recording__image">
              <p class="recording__meta"></p>
          </div>
      </div>""");
    addTest("div.recording>h2.recording__title>(span)+div.recording__image(>p.recording__meta)", """
      <div class="recording">
          <h2 class="recording__title"><span></span>
              <div class="recording__image">
                  <p class="recording__meta"></p>
              </div>
          </h2>
      </div>""");
  }

  private void addPlusOperatorTests() {
    addTest("p+p", "<p></p><p></p>");
    addTest("p+P", "<p></p><P></P>");
    addTest("p.name+p+p", "<p class=\"name\"></p><p></p><p></p>");
  }

  private void addGreaterThanOperatorTests() {
    addTest("p>em", "<p><em></em></p>");
    addTest("p.hello>em.world>span", "<p class=\"hello\"><em class=\"world\"><span></span></em></p>");
  }

  private void addAttributesOperatorTests() {
    addTest("p.name", "<p class=\"name\"></p>");
    addTest("p.one.two.three", "<p class=\"one two three\"></p>");
    addTest("p.one-two.three", "<p class=\"one-two three\"></p>");
    addTest("p.one.two-three", "<p class=\"one two-three\"></p>");
    addTest("p.one_two-three", "<p class=\"one_two-three\"></p>");
    addTest("p#myid", "<p id=\"myid\"></p>");
    addTest("p#myid.name_with-dash32.otherclass", "<p id=\"myid\" class=\"name_with-dash32 otherclass\"></p>");
    addTest("span.one.two.three", "<span class=\"one two three\"></span>");

    addTest("span.one#two", "<span class=\"one\" id=\"two\"></span>");
    addTest("span.one.two#three", "<span class=\"one two\" id=\"three\"></span>");

    addTest("span[title]", "<span title=\"\"></span>");
    addTest("span[title data]", "<span title=\"\" data=\"\"></span>");
    addTest("span.test[title data]", "<span class=\"test\" title=\"\" data=\"\"></span>");
    addTest("span#one.two[title data]", "<span id=\"one\" class=\"two\" title=\"\" data=\"\"></span>");
    addTest("span[title=Hello]", "<span title=\"Hello\"></span>");
    addTest("span[title=\"Hello world\"]", "<span title=\"Hello world\"></span>");
    addTest("span[title=\"Hello world\"]", "<span title=\"Hello world\"></span>");
    addTest("span[title=\"Hello world\" data=other]", "<span title=\"Hello world\" data=\"other\"></span>");
    addTest("span[title=\"Hello world\" data=other attr2 attr3]",
            "<span title=\"Hello world\" data=\"other\" attr2=\"\" attr3=\"\"></span>");
    addTest("span[title=\"Hello world\" data=other attr2 attr3]>em",
            "<span title=\"Hello world\" data=\"other\" attr2=\"\" attr3=\"\"><em></em></span>");
    addTest("filelist[id=javascript.files]", "<filelist id=\"javascript.files\"></filelist>");
  }

  private void addDefaultAttributesTests() {
    addTest("a[\"text.html\"]", "<a href=\"text.html\"></a>");
    addTest("a['text.html']", "<a href=\"text.html\"></a>");
    addTest("a[text.html]", "<a href=\"text.html\"></a>");
    addTest("a[http://google.com title=Google]", "<a href=\"http://google.com\" title=\"Google\"></a>");
    addTest("a[title=Google http://google.com]", "<a href=\"http://google.com\" title=\"Google\"></a>");
    addTest("img[image.png]", "<img src=\"image.png\" alt=\"\">");
    addTest("link[style.css]", "<link rel=\"stylesheet\" href=\"style.css\">");
    addTest("script", "<script></script>");
    addTest("script[src]", "<script src=\"\"></script>");
    addTest("script[file.js]", "<script src=\"file.js\"></script>");
    addTest("script[/packages/requiejs/require.js]", "<script src=\"/packages/requiejs/require.js\"></script>");
    addTest("al", "<a></a>");
    addTest("al[file.html]", "<a href=\"http://file.html\"></a>");
    addTest("al2", "<a !href></a>");
    addTest("al2[file.html]", "<a !href></a>");
  }

  private void addQuotesTests() {
    addTestWithInit("1. div.cls", "1. <div class=\"cls\"></div>", quoteStyle(CodeStyleSettings.QuoteStyle.Double));
    addTestWithInit("2. div.cls", "2. <div class='cls'></div>", quoteStyle(CodeStyleSettings.QuoteStyle.Single));
    addTestWithInit("3. div.cls", "3. <div class=\"cls\"></div>", quoteStyle(CodeStyleSettings.QuoteStyle.None));
  }

  private void addBooleanAttributesTests() {
    addTestWithInit("track[default]", "<track default>", compactBooleanAllowed(true));
    addTestWithInit("track[default=customValue]", "<track default=\"customValue\">", compactBooleanAllowed(true));
    addTestWithInit("div.editor[a. title=test]", "<div class=\"editor\" a title=\"test\">|</div>", compactBooleanAllowed(true));

    addTestWithInit("b[a.]", "<b a=\"a\"></b>", compactBooleanAllowed(false));
    addTestWithInit("track[default]", "<track default=\"default\">", compactBooleanAllowed(false));

    addTestWithInit("button:d", "<button disabled></button>", compactBooleanAllowed(true));
    addTestWithInit("button:d", "<button disabled=\"disabled\"></button>", compactBooleanAllowed(false));

    addTestWithInit("input[title]", "<input type=\"text\" title=\"\">", null);
    addTestWithInit("input[title]", "<input type=\"text\" title>", (fixture, testRootDisposable) -> {
      final HtmlUnknownBooleanAttributeInspection inspection = new HtmlUnknownBooleanAttributeInspection();
      final String oldValue = inspection.getAdditionalEntries();
      inspection.updateAdditionalEntries("title");
      fixture.enableInspections(inspection);
      Disposer.register(testRootDisposable, () -> inspection.updateAdditionalEntries(oldValue));
    });
  }

  private void addEndVariableTests() {
    addTestWithPositionCheck("tr>td+td+td", """
      <tr>
          <td>|</td>
          <td>|</td>
          <td>|</td>
      </tr>""", withAddEndEditPoint(false));
    addTestWithPositionCheck("tr>td+td+td", """
      <tr>
          <td>|</td>
          <td>|</td>
          <td>|</td>
      </tr>|""", withAddEndEditPoint(true));
  }

  @NotNull
  private static TestInitializer withAddEndEditPoint(final boolean value) {
    return (fixture, testRootDisposable) -> {
      final boolean oldValue = EmmetOptions.getInstance().isAddEditPointAtTheEndOfTemplate();
      EmmetOptions.getInstance().setAddEditPointAtTheEndOfTemplate(value);
      Disposer.register(testRootDisposable, () -> EmmetOptions.getInstance().setAddEditPointAtTheEndOfTemplate(oldValue));
    };
  }

  private void addExpandosTests() {
    addTest("dl+", "<dl><dt></dt><dd></dd></dl>");
    addTest("div+div>dl+", "<div></div><div><dl><dt></dt><dd></dd></dl></div>");
  }

  private void addCounterTests() {
    addTest("h$", "<h0></h0>");
    addTest("h$.item$", "<h0 class=\"item0\"></h0>");

    addTest("h$.item$*3", "<h1 class=\"item1\"></h1><h2 class=\"item2\"></h2><h3 class=\"item3\"></h3>");
    addTest("h$.item${text $$$}*3", "<h1 class=\"item1\">text 001</h1><h2 class=\"item2\">text 002</h2><h3 class=\"item3\">text 003</h3>");
    addTest("h$*3>b$", "<h1><b1></b1></h1><h2><b2></b2></h2><h3><b3></b3></h3>");

    addTest("ul#nav>li.item$*3", "<ul id=\"nav\"><li class=\"item1\"></li><li class=\"item2\"></li><li class=\"item3\"></li></ul>");
    addTest("ul#nav>li.item$$$*3", "<ul id=\"nav\"><li class=\"item001\"></li><li class=\"item002\"></li><li class=\"item003\"></li></ul>");
    addTest("ul#nav>li.$$item$$$*3",
            "<ul id=\"nav\"><li class=\"01item001\"></li><li class=\"02item002\"></li><li class=\"03item003\"></li></ul>");
    addTest("ul#nav>li.pre$*3+li.post$*3",
            "<ul id=\"nav\"><li class=\"pre1\"></li><li class=\"pre2\"></li><li class=\"pre3\"></li><li class=\"post1\"></li><li class=\"post2\"></li><li class=\"post3\"></li></ul>");
    addTest(".sample$*3", "<div class=\"sample1\"></div><div class=\"sample2\"></div><div class=\"sample3\"></div>");
    addTest("ul#nav>li{text}*3", "<ul id=\"nav\"><li>text</li><li>text</li><li>text</li></ul>");
    addTest("p{Max Length Error:  {{myForm.sample.$error.maxlength}}}", "<p>Max Length Error: {{myForm.sample.0error.maxlength}}</p>");
    addTest("p{Max Length Error:  {{myForm.sample.\\$error.maxlength}}}", "<p>Max Length Error: {{myForm.sample.$error.maxlength}}</p>");
    addTest("p{Max Length Error:  {{myForm.sample.\\\\$error.maxlength}}}",
            "<p>Max Length Error: {{myForm.sample.\\0error.maxlength}}</p>");

    // counter base
    addTest("{$@3 }*3", "3 4 5 ");
    addTest("{$@- }*3", "3 2 1 ");
    addTest("{$@-5 }*3", "7 6 5 ");
    addTest("{$$@-5 }*3", "07 06 05 ");

    // numbering from groups
    addTest("(span.i$)*3", "<span class=\"i1\"></span><span class=\"i2\"></span><span class=\"i3\"></span>");
    addTest("p.p$*2>(i.i$+b.b$)*3",
            "<p class=\"p1\"><i class=\"i1\"></i><b class=\"b1\"></b><i class=\"i2\"></i><b class=\"b2\"></b><i class=\"i3\"></i><b class=\"b3\"></b></p><p class=\"p2\"><i class=\"i1\"></i><b class=\"b1\"></b><i class=\"i2\"></i><b class=\"b2\"></b><i class=\"i3\"></i><b class=\"b3\"></b></p>");
    addTest("(p.i$+ul>li.i$*2>span.s$)*3",
            "<p class=\"i1\"></p><ul><li class=\"i1\"><span class=\"s1\"></span></li><li class=\"i2\"><span class=\"s2\"></span></li></ul><p class=\"i2\"></p><ul><li class=\"i1\"><span class=\"s1\"></span></li><li class=\"i2\"><span class=\"s2\"></span></li></ul><p class=\"i3\"></p><ul><li class=\"i1\"><span class=\"s1\"></span></li><li class=\"i2\"><span class=\"s2\"></span></li></ul>");

    // skip repeater replace
    addTest("span[class=item\\$]*2", "<span class=\"item$\"></span><span class=\"item$\"></span>");
    addTest("span{item \\$}*2", "<span>item $</span><span>item $</span>");


    // add offset to numbering
    addTest("span.item$@3*2", "<span class=\"item3\"></span><span class=\"item4\"></span>");
    addTest("span.item$$@3*2", "<span class=\"item03\"></span><span class=\"item04\"></span>");
    addTest("span.item$@*2", "<span class=\"item1\"></span><span class=\"item2\"></span>");

    // numbering in descending order
    addTest("span.item$@-*2", "<span class=\"item2\"></span><span class=\"item1\"></span>");
    addTest("span.item$@-3*2", "<span class=\"item4\"></span><span class=\"item3\"></span>");
    addTest("span.item$@-9*2", "<span class=\"item10\"></span><span class=\"item9\"></span>");
    addTest("span.item$$@-9*2", "<span class=\"item10\"></span><span class=\"item09\"></span>");
    addTest("span$.item$@-*2", "<span1 class=\"item2\"></span1><span2 class=\"item1\"></span2>");
  }

  private void addOtherTests() {
    // variables on empty attributes
    addTestWithPositionCheck("ul[a]>li[b=]>div[b=\"\"]+div[a=\"a\"]+div.", """
      <ul a="|">
          <li b="|">
              <div b="|">|</div>
              <div a="a">|</div>
              <div class="|">|</div>
          </li>
      </ul>""");
    addTest("some:elem", "<some:elem></some:elem>");
    addTest("li#id$.class$*3",
            "<li id=\"id1\" class=\"class1\"></li><li id=\"id2\" class=\"class2\"></li><li id=\"id3\" class=\"class3\"></li>");
    addTest("select#test", "<select name=\"\" id=\"test\"></select>");
    addTest("h${Header $}*3", "<h1>Header 1</h1><h2>Header 2</h2><h3>Header 3</h3>");
  }

  private void addGroupTests() {
    addTest("div#head+(p>p)+div#footer", "<div id=\"head\"></div><p>\n<p></p>\n</p><div id=\"footer\"></div>");
    addTest("div#head>((ul#nav>li*3)+(div.subnav>p)+(div.othernav))+div#footer",
            "<div id=\"head\"><ul id=\"nav\"><li></li><li></li><li></li></ul><div class=\"subnav\">\n<p></p>\n</div><div class=\"othernav\"></div><div id=\"footer\"></div></div>");
    addTest("div#head>(ul#nav>li*3>(div.subnav>p)+(div.othernav))+div#footer",
            "<div id=\"head\"><ul id=\"nav\"><li><div class=\"subnav\">\n<p></p>\n</div><div class=\"othernav\"></div></li><li><div class=\"subnav\">\n<p></p>\n</div><div class=\"othernav\"></div></li><li><div class=\"subnav\">\n<p></p>\n</div><div class=\"othernav\"></div></li></ul><div id=\"footer\"></div></div>");
    addTest("ul>li.pre$*2+(li.item$*4>a)+li.post$*2",
            "<ul><li class=\"pre1\"></li><li class=\"pre2\"></li><li class=\"item1\"><a href=\"\"></a></li><li class=\"item2\"><a href=\"\"></a></li><li class=\"item3\"><a href=\"\"></a></li><li class=\"item4\"><a href=\"\"></a></li><li class=\"post1\"></li><li class=\"post2\"></li></ul>");
    addTest("div>(i+b)*2+(span+em)*3",
            "<div><i></i><b></b><i></i><b></b><span></span><em></em><span></span><em></em><span></span><em></em></div>");
  }

  private void addGroupMultiplicationTests() {
    addTest("(span.i$)*3", "<span class=\"i1\"></span><span class=\"i2\"></span><span class=\"i3\"></span>");
    addTest("p.p$*2>(i.i$+b.b$)*3",
            "<p class=\"p1\"><i class=\"i1\"></i><b class=\"b1\"></b><i class=\"i2\"></i><b class=\"b2\"></b><i class=\"i3\"></i><b class=\"b3\"></b></p><p class=\"p2\"><i class=\"i1\"></i><b class=\"b1\"></b><i class=\"i2\"></i><b class=\"b2\"></b><i class=\"i3\"></i><b class=\"b3\"></b></p>");
    addTest("(p.i$+ul>li.i$*2>span.s$)*3",
            "<p class=\"i1\"></p><ul><li class=\"i1\"><span class=\"s1\"></span></li><li class=\"i2\"><span class=\"s2\"></span></li></ul><p class=\"i2\"></p><ul><li class=\"i1\"><span class=\"s1\"></span></li><li class=\"i2\"><span class=\"s2\"></span></li></ul><p class=\"i3\"></p><ul><li class=\"i1\"><span class=\"s1\"></span></li><li class=\"i2\"><span class=\"s2\"></span></li></ul>");
  }

  private void addImpliedTagNameTests() {
    addTest("#content", "<div id=\"content\"></div>");
    addTest(".content", "<div class=\"content\"></div>");
    addTest("#content.demo", "<div id=\"content\" class=\"demo\"></div>");
    addTest(".demo[title=test]", "<div class=\"demo\" title=\"test\"></div>");
    addTest("#some_id>.some_class", "<div id=\"some_id\"><div class=\"some_class\"></div></div>");
    addTest("ul>.item", "<ul><li class=\"item\"></li></ul>");
    addTest("ol>.", "<ol><li class=\"\"></li></ol>");
    addTest("em>.", "<em><span class=\"\"></span></em>");
    addTest("table>#row$*4>[colspan=2]", """
      <table>
      \t<tr id="row1">
      \t\t<td colspan="2"></td>
      \t</tr>
      \t<tr id="row2">
      \t\t<td colspan="2"></td>
      \t</tr>
      \t<tr id="row3">
      \t\t<td colspan="2"></td>
      \t</tr>
      \t<tr id="row4">
      \t\t<td colspan="2"></td>
      \t</tr>
      </table>""");
    addTest("<ul>.cls<caret></ul>", "<ul><li class=\"cls\"></li></ul>");
    addTest("<ul>.cls>.cls^.cls<caret></ul>", "<ul><li class=\"cls\"><div class=\"cls\"></div></li><li class=\"cls\"></li></ul>");
    addTest("<ul>.cls>.cls^^.cls<caret></ul>", "<ul><li class=\"cls\"><div class=\"cls\"></div></li><li class=\"cls\"></li></ul>");
  }

  private void addTextNodesTests() {
    addTest("span>{Hello world}", "<span>Hello world</span>");
    addTest("span{Hello world}", "<span>Hello world</span>");
    addTest("span>{Hello}+{ world}", "<span>Hello world</span>");
    addTest("span>{Click }+(a[href=/url/]{here})+{ for more info}", "<span>Click <a href=\"/url/\">here</a> for more info</span>");
  }

  private void addClimbUpOperatorTests() {
    addTest("p>em^div", "<p><em></em></p><div></div>");
    addTest("p>em>span^^div", "<p><em><span></span></em></p><div></div>");
    addTest("p>em>span^^^^div", "<p><em><span></span></em></p><div></div>");
  }

  private void addTest(String source, String expected) {
    addTest(source, expected, "html");
  }

  private void addTestWithInit(String source, String expected, @Nullable TestInitializer setUp) {
    addTest(source, expected, setUp, "html");
  }

  private void addTestWithPositionCheck(String source, String expected) {
    addTestWithPositionCheck(source, expected, "html");
  }

  private void addTestWithPositionCheck(String source, String expected, @Nullable TestInitializer setUp) {
    addTestWithPositionCheck(source, expected, setUp, "html");
  }

  @NotNull
  private static TestInitializer compactBooleanAllowed(final boolean compactBooleanAllowed) {
    return (fixture, testRootDisposable) -> HtmlUtil
      .setShortNotationOfBooleanAttributeIsPreferred(compactBooleanAllowed, testRootDisposable);
  }

  @NotNull
  private static String getTestDataPath() {
    return Joiner.on(File.separatorChar).join(XmlTestUtil.getXmlTestDataPath(), "codeInsight", "template", "emmet", "abbreviation");
  }
}
