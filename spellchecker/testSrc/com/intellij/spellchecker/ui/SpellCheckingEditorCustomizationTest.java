/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.spellchecker.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.ui.EditorCustomization;

@SuppressWarnings("SpellCheckingInspection")
public class SpellCheckingEditorCustomizationTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testEnabled() {
    doTest(true, "<TYPO descr=\"Typo: In word 'missspelling'\">missspelling</TYPO>");
  }

  public void testDisabled() {
    doTest(false, "missspelling");
  }

  public void testEnabledEvenIfDisabledInMainProfile() {
    //todo[batrak] ((CodeInsightTestFixtureImpl)myFixture).myDisabledInspections.add(SpellCheckingInspection.SPELL_CHECKING_INSPECTION_TOOL_NAME);
    testEnabled();
  }

  private void doTest(boolean enabled, String document) {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    try {
      myFixture.configureByText(PlainTextFileType.INSTANCE, document);
      myFixture.enableInspections(new SpellCheckingInspection());

      EditorCustomization customization = SpellCheckingEditorCustomizationProvider.getInstance().getCustomization(enabled);
      assertNotNull(customization);
      customization.customize((EditorEx)myFixture.getEditor());

      myFixture.checkHighlighting();
    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
    }
  }
}