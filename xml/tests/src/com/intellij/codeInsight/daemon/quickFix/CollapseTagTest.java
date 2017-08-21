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

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.xml.util.CollapseTagIntention;

/**
 * @author Dmitry Avdeev
 */
public class CollapseTagTest extends LightPlatformCodeInsightFixtureTestCase{

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
}
