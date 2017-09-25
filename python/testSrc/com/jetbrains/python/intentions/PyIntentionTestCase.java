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
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NonNls;

public abstract class PyIntentionTestCase extends PyTestCase {
  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/" + getClass().getSimpleName();
  }

  protected void doTest(String hint, LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doIntentionTest(hint);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  protected void doIntentionTest(final String hint, String... files) {
    final String testFileName = getTestName(true);
    final PsiFile file;
    if (files.length>0) {
      final PsiFile[] allFiles = myFixture.configureByFiles(files);
      file = allFiles[0];
    } else {
      file = myFixture.configureByFile(testFileName + ".py");
    }
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    assertSdkRootsNotParsed(file);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }

  protected void doNegativeTest(final String hint) {
    final String testFileName = getTestName(true);
    final PsiFile file = myFixture.configureByFile(testFileName + ".py");
    final IntentionAction intentionAction = myFixture.getAvailableIntention(hint);
    assertNull(intentionAction);
    assertSdkRootsNotParsed(file);
  }
}
