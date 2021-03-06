// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html;

import com.intellij.lang.ParserDefinition;
import com.intellij.lang.html.HTMLParserDefinition;
import com.intellij.lexer.EmbeddedTokenTypesProvider;
import com.intellij.xml.XmlParsingTest;
import org.jetbrains.annotations.NotNull;

public class HtmlParsingTest extends XmlParsingTest {

  public HtmlParsingTest() {
    super("psi/html", "html", new HTMLParserDefinition());
  }

  protected HtmlParsingTest(@NotNull String dataPath,
                            @NotNull String fileExt,
                            ParserDefinition @NotNull ... definitions) {
    super(dataPath, fileExt, definitions);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtensionPoint(EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME, EmbeddedTokenTypesProvider.class);
  }

  @Override
  protected final void doTestXml(String text) throws Exception {
    doTestHtml(text);
  }

  protected void doTestHtml(String text) throws Exception {
    doTest(text, "test.html");
  }

  @Override
  public void testDtdUrl1() {
    //disable test
  }

  @Override
  public void testCustomMimeType() {
    //disable test
  }

  public void testHtmlDoctype1() throws Exception {
    doTestHtml("<!DOCTYPE html>\n");
  }

  public void testHtmlDoctype2() throws Exception {
    doTestHtml(" <!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">\n");
  }

  public void testHtmlDoctype3() throws Exception {
    doTestHtml(" <!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
  }

  public void testHtmlCharEntityRef() throws Exception {
    doTestHtml("&#xAAff;&#XaaFF;&#x&#X<tag attr='&#xAAff;&#XaaFF;&#x&#X'/>");
  }

  public void testHtmlComments() throws Exception {
    doTestHtml("<!--Valid comment-->\n" +
               "<!--Valid comment<!-->\n" +
               "<!--Invalid content <!-- -->\n" +
               "<!--Invalid comment starts: --> <!--> <!--->\n" +
               "<!--Invalid end <!--->\n" +
               "<!--Invalid end --!>\n");
  }

  public void testHtmlIEConditionalComments1() throws Exception {
    doTestHtml("<!--[if IE 6]>\n" +
               "<p>You are using Internet Explorer 6.</p>\n" +
               "<![endif]-->");
  }

  public void testHtmlIEConditionalComments2() throws Exception {
    doTestHtml("<!--[if lte IE 7]>\n" +
               "<style type=\"text/css\">\n" +
               "/* CSS here */\n" +
               "</style>\n" +
               "<![endif]-->");
  }

  public void testHtmlIEConditionalComments3() throws Exception {
    doTestHtml("<!--[if !IE]>-->\n" +
               "<link href=\"non-ie.css\" rel=\"stylesheet\">\n" +
               "<!--<![endif]-->");
  }

  public void testScriptEmbeddingParsing() throws Exception {
    doTestHtml("<script type=\"foo/bar\"><div></div></script>\n" +
               "<script type=\"foo/bar\"><div> </div></script>");
  }

  public void testSpecialTagsParsing() throws Exception {
    doTestHtml("<head><title>This is my <title>&lt;<!--</title><body>\n" +
               "<script type=\"foo/bar\"><div> </div></script>\n" +
               "<style type='foo/bar'><my><style></style>\n"+
               "<textarea>this {{text}} {area} &nbsp; <is></cool></textarea>");
  }

  public void testPAutoClose() throws Exception {
    doTestHtml("<div>\n" +
               "<p><br/><div><span><p></p></span></div>\n" +
               "<P><table></table>\n" +
               "</div>\n");
  }

}
