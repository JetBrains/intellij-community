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
package com.intellij.spellchecker.inspection.quickfixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.quickfixes.ChangeTo;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;

public abstract class AbstractSpellCheckerFixesTest extends SpellcheckerInspectionTestCase {
  protected abstract String getExtension();

  @Override
  protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "inspection" + File.separator + "quickfixes";
  }

  private void doChangeToTest(int toSelect) {
    myFixture.configureByFile(getBeforeFile());
    myFixture.enableInspections(SpellCheckingInspection.class);
    final IntentionAction intention = ContainerUtil.filter(
      myFixture.getAvailableIntentions(), (it) -> it.getFamilyName().equals(ChangeTo.getFixName())
    ).get(toSelect + 1);
    assertNotNull("cannot find quick fix", intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getResultFile());
  }

  protected void doNoQuickFixTest(String quickfix) {
    myFixture.configureByFile(getBeforeFile());
    myFixture.enableInspections(SpellCheckingInspection.class);
    assertNull(myFixture.getAvailableIntention(quickfix));
  }

  protected void doChangeToTest() {
    doChangeToTest(0);
  }

  private String getResultFile() {
    return getTestName(true) + ".after" + getExtension();
  }

  private String getBeforeFile() {
    return getTestName(true) + getExtension();
  }
}
