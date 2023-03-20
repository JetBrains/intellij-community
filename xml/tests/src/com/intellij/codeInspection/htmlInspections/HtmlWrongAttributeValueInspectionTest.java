/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class HtmlWrongAttributeValueInspectionTest extends BasePlatformTestCase {

  public void testTextareaElement() {
    quickfixTest("<textarea wrap='hard'></textarea><textarea wrap='<warning descr=\"Wrong attribute value\">me<caret>dium</warning>'></textarea>",
                 "<textarea wrap='hard'></textarea><textarea wrap='hard'></textarea>");
  }

  public void testSvgGElement() {
    quickfixTest("<svg><g font-style='normal'/><g font-style='<warning descr=\"Wrong attribute value\">ob<caret>liq</warning>'></g></svg>",
                 "<svg><g font-style='normal'/><g font-style='oblique'></g></svg>");
  }

  public void testSuppression() {
    highlightTest("<textarea wrap='<warning descr=\"Wrong attribute value\">medium</warning>'></textarea>" +
                  "<div><!--suppress HtmlWrongAttributeValue--><textarea wrap='medium'></textarea></div>");
  }

  @NotNull
  protected LocalInspectionTool getInspection() {
    return new HtmlWrongAttributeValueInspection();
  }

  protected void highlightTest(@Language("HTML") String code) {
    final LocalInspectionTool inspection = getInspection();
    myFixture.enableInspections(inspection);
    final HighlightDisplayKey displayKey = HighlightDisplayKey.find(inspection.getShortName());
    final Project project = myFixture.getProject();
    final InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
    final HighlightDisplayLevel errorLevel = currentProfile.getErrorLevel(displayKey, null);
    if (errorLevel == HighlightDisplayLevel.DO_NOT_SHOW) {
      currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project);
    }
    myFixture.configureByText(HtmlFileType.INSTANCE, code);
    myFixture.testHighlighting();
  }

  protected void quickfixTest(@Language("HTML") String before, @Language("HTML") String after) {
    highlightTest(before);
    final IntentionAction intention = ContainerUtil.getFirstItem(myFixture.getAvailableIntentions());
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult(after);
  }

}
