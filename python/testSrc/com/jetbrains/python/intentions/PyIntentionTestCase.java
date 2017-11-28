// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;

public abstract class PyIntentionTestCase extends PyTestCase {
  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/" + getClass().getSimpleName();
  }

  protected void doTest(String hint, LanguageLevel languageLevel) {
    runWithLanguageLevel(languageLevel, () -> doIntentionTest(hint));
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
