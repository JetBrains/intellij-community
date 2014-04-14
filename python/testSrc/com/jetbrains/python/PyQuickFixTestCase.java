/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * User: ktisha
 */
public abstract class PyQuickFixTestCase extends PyTestCase {
  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/quickFixes/" + getClass().getSimpleName();
  }

  protected void doQuickFixTest(final Class inspectionClass, final String hint, LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doQuickFixTest(inspectionClass, hint);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  protected void doQuickFixTest(final Class inspectionClass, final String hint) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }

  protected void doInspectionTest(final Class inspectionClass) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
  }

  protected void doMultifilesTest(@NotNull final Class inspectionClass, @NotNull final String hint, @NotNull final String[] files) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    String [] filenames = Arrays.copyOf(files, files.length + 1);

    filenames[files.length] = testFileName + ".py";
    myFixture.configureByFiles(filenames);
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + ".py", testFileName + "_after.py", true);
  }
}
