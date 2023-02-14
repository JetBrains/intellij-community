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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xml.util.CheckTagEmptyBodyInspection;
import com.intellij.xml.util.CollapseTagIntention;

/**
 * @author Dmitry Avdeev
 */
public class CollapseTagTest extends BasePlatformTestCase {

  public void testAvailable() {
    PsiFile file = myFixture.configureByText(XmlFileType.INSTANCE, "<a>    <caret>   </a>");
    assertTrue(new CollapseTagIntention().isAvailable(getProject(), myFixture.getEditor(), file));
  }

  public void testNotAvailable() {
    PsiFile file = myFixture.configureByText(XmlFileType.INSTANCE, "<a>    <caret>   <b/> </a>");
    assertFalse(new CollapseTagIntention().isAvailable(getProject(), myFixture.getEditor(), file));
  }

  public void testAlreadyCollapsed() {
    PsiFile file = myFixture.configureByText(XmlFileType.INSTANCE, "<a/>");
    assertFalse(new CollapseTagIntention().isAvailable(getProject(), myFixture.getEditor(), file));
  }

  public void testCollapseInnerTag() {
    myFixture.enableInspections(new CheckTagEmptyBodyInspection());
    PsiFile file = myFixture.configureByText(XmlFileType.INSTANCE, """
      <a>
          <b><caret></b>
      </a>""");
    assertTrue(new CollapseTagIntention().isAvailable(getProject(), myFixture.getEditor(), file));
    IntentionAction action = myFixture.findSingleIntention("Collapse");
    assertNotNull(action);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> action.invoke(getProject(), myFixture.getEditor(), file));
    myFixture.checkResult("""
                            <a>
                                <b/>
                            </a>""");
  }
}
