/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.EmptyNode
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
public class XmlLiveTemplateTest extends LightCodeInsightFixtureTestCase {

  private TemplateState getState() {
    TemplateManagerImpl.getTemplateState(myFixture.getEditor())
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    if (state != null) {
      state.gotoEnd();
    }
    super.tearDown();
  }

  public void "test tag template in xml"() {
    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("frm", "user", '$VAR$');
    template.addVariable('VAR', new EmptyNode(), new ConstantNode("<"), true)
    
    myFixture.configureByText 'a.xml', '<tag><caret></tag>'
    manager.startTemplate(myFixture.getEditor(), template);
    myFixture.checkResult '<tag><selection><</selection><caret></tag>'
  }

  public void "test insert CDATA by CD and tab"() {
    myFixture.configureByText 'a.xml', '<tag><caret></tag>'
    myFixture.type('CD\t')
    myFixture.checkResult '''<tag><![CDATA[

]]></tag>'''
  }

  public void testAvailabilityCDATA() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("CD", "html/xml");
    assertTrue(isApplicable("<foo><caret> </foo>", template));
    assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template));
  }

  public void testAvailabilityT() {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("T", "html/xml");
    assertTrue(isApplicable("<foo><caret> </foo>", template));
    assertFalse(isApplicable("<foo bar=\"<caret>\"></foo>", template));
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst);
  }


}
