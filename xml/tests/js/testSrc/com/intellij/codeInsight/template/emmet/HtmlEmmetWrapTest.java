// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.HtmlContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestFixture.emmetAbbreviationFixture;

@TestApplication
public class HtmlEmmetWrapTest {
  private final TestFixture<EmmetAbbreviationTestFixture> emmet = emmetAbbreviationFixture("html");

  @Test
  public void testText() {
    emmet.get().emmetWrap("Hello world!", "a", "<a href=\"\">Hello world!</a>");
  }

  @Test
  public void testWrapTag() {
    emmet.get().emmetWrap("<h3>Hello world!</h3>", "a", "<a href=\"\"><h3>Hello world!</h3></a>");
  }

  @Test
  public void testWrapComment() {
    emmet.get().emmetWrap("<h3><selection><!--Hello world!--></selection></h3>", "a", "<h3><a href=\"\"><!--Hello world!--></a></h3>");
  }

  @Test
  public void testWrapText() {
    emmet.get().emmetWrap("<span><selection>SA.</selection></span>", "div", "<span><div>SA.</div></span>");
  }

  @Test
  public void testFormatting() {
    emmet.get().emmetWrap("""
                <selection>one
                two
                three
                </selection>""", "div", """
                <div><caret>one
                    two
                    three
                </div>""");
  }

  @Test
  public void testDoNoTrimSelection() {
    emmet.get().emmetWrap("<selection>hello </selection>world", "b", "<b>hello </b>world");
  }

  public void _testFormattingWeb6563() {
    emmet.get().emmetWrap("""
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
  @Test
  public void testExpandAbbreviationWithSelectionAndEndVariables() {
    addSelectionTemplate("dquo", "&ldquo;$SELECTION$&rdquo;$END$");
    emmet.get().expandAndCheck("dquo", "&ldquo;<caret>&rdquo;");
  }

  @Test
  public void testWrapAbbreviationWithSelectionAndEndVariables() {
    final TemplateImpl template = addSelectionTemplate("dquo", "&ldquo;$SELECTION$&rdquo;$END$");
    emmet.get().templateWrap("text", template, "&ldquo;text&rdquo;<caret>");
  }

  @Test
  public void testExpandAbbreviationWithSelectionVariable() {
    addSelectionTemplate("dquo", "&ldquo;$SELECTION$&rdquo;");
    emmet.get().expandAndCheck("dquo", "&ldquo;<caret>&rdquo;");
  }

  @Test
  public void testWrapAbbreviationWithSelectionVariable() {
    final TemplateImpl template = addSelectionTemplate("dquo", "&ldquo;$SELECTION$&rdquo;");
    emmet.get().templateWrap("text", template, "<caret>&ldquo;text&rdquo;");
  }

  @Test
  public void testMultiCaretWrapping() {
    emmet.get().emmetWrap("""
                <selection><caret><div></div></selection>

                <selection><caret><div></div></selection>""", "div", """
                <div><caret>
                    <div></div>
                </div>

                <div><caret>
                    <div></div>
                </div>""");
  }

  @Test
  public void testMultiCaretWrappingShouldStopAtFirstVariable() {
    emmet.get().emmetWrap("""
                <selection><caret>link text</selection>

                <selection><caret>other link text</selection>""", "a", """
                <a href="<caret>">link text</a>

                <a href="<caret>">other link text</a>""");
  }

  @Test
  public void testWrapUrlLikeContent() {
    emmet.get().emmetWrap("http://emmet.io", "a", "<a href=\"http://emmet.io\">http://emmet.io</a>");
    emmet.get().emmetWrap("www.emmet.io", "a", "<a href=\"http://www.emmet.io\">www.emmet.io</a>");
    emmet.get().emmetWrap("emmet.io", "a", "<a href=\"\">emmet.io</a>");
    emmet.get().emmetWrap("info@emmet.io", "a", "<a href=\"mailto:info@emmet.io\">info@emmet.io</a>");
  }

  private TemplateImpl addSelectionTemplate(String key, String text) {
    final TemplateManager manager = TemplateManager.getInstance(emmet.get().getProject());
    TemplateImpl templateImpl = (TemplateImpl)manager.createTemplate(key, "html", text);
    templateImpl.addVariable(TemplateImpl.SELECTION, "", "", false);
    TemplateContext context = templateImpl.getTemplateContext();
    context.setEnabled(TemplateContextTypes.getByClass(HtmlContextType.class), true);
    CodeInsightTestUtil.addTemplate(templateImpl, emmet.get().getFixture().getTestRootDisposable());
    return templateImpl;
  }
}
