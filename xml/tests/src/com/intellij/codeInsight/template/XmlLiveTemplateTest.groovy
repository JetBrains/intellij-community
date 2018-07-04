/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.template

import com.intellij.application.options.emmet.EmmetOptions
import com.intellij.codeInsight.template.impl.*
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class XmlLiveTemplateTest extends LightCodeInsightFixtureTestCase {

  private TemplateState getState() {
    TemplateManagerImpl.getTemplateState(myFixture.getEditor())
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(getProject(), myFixture.getTestRootDisposable())
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
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("CD", "html/xml")
    assertTrue(isApplicable("<foo><caret> </foo>", template))
    assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template))
  }

  void testAvailabilityT() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("T", "html/xml")
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
    return CodeStyleSettingsManager.getInstance(getProject())
      .getCurrentSettings()
      .getCustomSettings(HtmlCodeStyleSettings.class)
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    myFixture.configureByText(XmlFileType.INSTANCE, text)
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst)
  }
}
