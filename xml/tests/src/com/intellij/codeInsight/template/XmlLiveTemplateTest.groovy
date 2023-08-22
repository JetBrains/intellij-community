// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.emmet.EmmetOptions
import com.intellij.codeInsight.template.impl.*
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class XmlLiveTemplateTest extends LightJavaCodeInsightFixtureTestCase {

  private TemplateState getState() {
    TemplateManagerImpl.getTemplateState(myFixture.getEditor())
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable())
  }

  @Override
  protected void tearDown() throws Exception {
    if (state != null) {
      state.gotoEnd()
    }
    super.tearDown()
  }

  void "test tag template in xml"() {
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("frm", "user", '$VAR$')
    template.addVariable('VAR', new EmptyNode(), new ConstantNode("<"), true)
    
    myFixture.configureByText 'a.xml', '<tag><caret></tag>'
    manager.startTemplate(myFixture.getEditor(), template)
    myFixture.checkResult '<tag><selection><</selection><caret></tag>'
  }

  void "test insert CDATA by CD and tab"() {
    myFixture.configureByText 'a.xml', '<tag><caret></tag>'
    myFixture.type('CD\t')
    myFixture.checkResult '''<tag><![CDATA[

]]></tag>'''
  }

  void testAvailabilityCDATA() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("CD", "HTML/XML")
    assertTrue(isApplicable("<foo><caret> </foo>", template))
    assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template))
  }

  void testAvailabilityT() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("T", "HTML/XML")
    assertTrue(isApplicable("<foo><caret> </foo>", template))
    assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template))
  }

  void testQuotedTemplateParametersWithEnforceQuotesOnReformat() throws Exception {
    HtmlCodeStyleSettings settings = getHtmlSettings()
    CodeStyleSettings.QuoteStyle originalQuoteStyle = settings.HTML_QUOTE_STYLE
    boolean emmetEnabled = EmmetOptions.instance.emmetEnabled
    boolean originalEnforceQuotes = settings.HTML_ENFORCE_QUOTES
    settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single
    settings.HTML_ENFORCE_QUOTES = true
    EmmetOptions.instance.setEmmetEnabled false
    try {
      myFixture.configureByText "a.html", "<div><caret></div>"
      myFixture.type("input:color\t")
      myFixture.type("inputName")
      myFixture.checkResult("<div><input type='color' name='inputName' id=''></div>")
    }
    finally {
      EmmetOptions.instance.setEmmetEnabled emmetEnabled
      settings.HTML_QUOTE_STYLE = originalQuoteStyle
      settings.HTML_ENFORCE_QUOTES = originalEnforceQuotes
    }
  }
  private HtmlCodeStyleSettings getHtmlSettings(){
    return CodeStyle.getSettings(getProject()).getCustomSettings(HtmlCodeStyleSettings.class)
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    myFixture.configureByText(XmlFileType.INSTANCE, text)
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst)
  }
}
