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
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.EmptyNode
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
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
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(true);
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
      ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(false);
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
  
}
