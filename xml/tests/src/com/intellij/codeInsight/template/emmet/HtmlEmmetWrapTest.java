// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.HtmlContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;

public class HtmlEmmetWrapTest extends EmmetAbbreviationTestCase {
  public void testText() {
    emmetWrap("Hello world!", "a", "<a href=\"\">Hello world!</a>");
  }

  public void testWrapTag() {
    emmetWrap("<h3>Hello world!</h3>", "a", "<a href=\"\"><h3>Hello world!</h3></a>");
  }

  public void testWrapComment() {
    emmetWrap("<h3><selection><!--Hello world!--></selection></h3>", "a", "<h3><a href=\"\"><!--Hello world!--></a></h3>");
  }

  public void testWrapText() {
    emmetWrap("<span><selection>SA.</selection></span>", "div", "<span><div>SA.</div></span>");
  }

  public void testFormatting() {
    emmetWrap("""
                <selection>one
                two
                three
                </selection>""", "div", """
                <div><caret>one
                    two
                    three
                </div>""");
  }

  public void testDoNoTrimSelection() {
    emmetWrap("<selection>hello </selection>world", "b", "<b>hello </b>world");
  }

  public void _testFormattingWeb6563() {
    emmetWrap("""
                <div id="testid">
                <selection>    <p>par</p>
                </selection></div>""", "first>span.cls>h1{text}+span.cls2", """
                <div id="testid">
                    <first><span class="cls">
                    <h1>text</h1>
                    <span class="cls2"><p>par</p></span></span></first>
                </div>""");
  }

  //see IDEA-112134
  public void testExpandAbbreviationWithSelectionAndEndVariables() {
    addSelectionTemplate("dquo", "&ldquo;$SELECTION$&rdquo;$END$");
    expandAndCheck("dquo", "&ldquo;<caret>&rdquo;");
  }

  public void testWrapAbbreviationWithSelectionAndEndVariables() {
    final TemplateImpl template = addSelectionTemplate("dquo", "&ldquo;$SELECTION$&rdquo;$END$");
    templateWrap("text", template, "&ldquo;text&rdquo;<caret>");
  }

  public void testExpandAbbreviationWithSelectionVariable() {
    addSelectionTemplate("dquo", "&ldquo;$SELECTION$&rdquo;");
    expandAndCheck("dquo", "&ldquo;<caret>&rdquo;");
  }

  public void testWrapAbbreviationWithSelectionVariable() {
    final TemplateImpl template = addSelectionTemplate("dquo", "&ldquo;$SELECTION$&rdquo;");
    templateWrap("text", template, "<caret>&ldquo;text&rdquo;");
  }

  public void testMultiCaretWrapping() {
    emmetWrap("""
                <selection><caret><div></div></selection>

                <selection><caret><div></div></selection>""", "div", """
                <div><caret>
                    <div></div>
                </div>

                <div><caret>
                    <div></div>
                </div>""");
  }

  public void testMultiCaretWrappingShouldStopAtFirstVariable() {
    emmetWrap("""
                <selection><caret>link text</selection>

                <selection><caret>other link text</selection>""", "a", """
                <a href="<caret>">link text</a>

                <a href="<caret>">other link text</a>""");
  }

  public void testWrapUrlLikeContent() {
    emmetWrap("http://emmet.io", "a", "<a href=\"http://emmet.io\">http://emmet.io</a>");
    emmetWrap("www.emmet.io", "a", "<a href=\"http://www.emmet.io\">www.emmet.io</a>");
    emmetWrap("emmet.io", "a", "<a href=\"\">emmet.io</a>");
    emmetWrap("info@emmet.io", "a", "<a href=\"mailto:info@emmet.io\">info@emmet.io</a>");
  }

  @Override
  protected String getExtension() {
    return "html";
  }

  private TemplateImpl addSelectionTemplate(String key, String text) {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    TemplateImpl templateImpl = (TemplateImpl)manager.createTemplate(key, "html", text);
    templateImpl.addVariable(TemplateImpl.SELECTION, "", "", false);
    TemplateContext context = templateImpl.getTemplateContext();
    context.setEnabled(TemplateContextTypes.getByClass(HtmlContextType.class), true);
    CodeInsightTestUtil.addTemplate(templateImpl, getTestRootDisposable());
    return templateImpl;
  }
}
