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
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Bas Leijdekkers
 */
public class HtmlMissingClosingTagInspectionTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testImgElement() {
    highlightTest("<html><body><img></body></html>");
  }

  public void testIncompleteElement() {
    highlightTest("<html><table><error descr=\"Element table is not closed\"><</error>/html>");
  }

  public void testPElement() {
    quickfixTest("<html><<warning descr=\"Element <p> is missing an end tag\">p</warning><caret>>Behold!</html>", "<html><p>Behold!</p></html>", "Add </p>");
  }



  @NotNull
  protected LocalInspectionTool getInspection() {
    return new HtmlMissingClosingTagInspection();
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

  protected void quickfixTest(@Language("HTML") String before, @Language("HTML") String after, String hint) {
    highlightTest(before);
    final IntentionAction intention = findIntention(hint);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult(after);
  }

  protected IntentionAction findIntention(@NotNull final String hint) {
    final List<IntentionAction> allIntentions = myFixture.getAvailableIntentions();
    final List<IntentionAction> intentions =
      allIntentions.stream().filter(action -> action.getText().startsWith(hint)).limit(2).collect(Collectors.toList());
    Assert.assertFalse("\"" + hint + "\" not in " + intentions, intentions.isEmpty());
    Assert.assertFalse("Too many quickfixes found for \"" + hint + "\": " + intentions + "]", intentions.size() > 1);
    return intentions.get(0);
  }
}
