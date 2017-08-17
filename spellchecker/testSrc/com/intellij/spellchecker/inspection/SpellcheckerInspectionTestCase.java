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
package com.intellij.spellchecker.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public abstract class SpellcheckerInspectionTestCase extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected boolean isCommunity() {
    return true;
  }

  public static String getSpellcheckerTestDataPath() {
    return "/spellchecker/testData/";
  }

  protected void doTest(String file) {
    myFixture.enableInspections(getInspectionTools());
    myFixture.testHighlighting(false, false, true, file);
  }

  static LocalInspectionTool[] getInspectionTools() {
    return new LocalInspectionTool[]{new SpellCheckingInspection()};
  }
}
