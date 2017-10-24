// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public abstract class PyQuickFixTestCase extends PyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.setCaresAboutInjection(false);
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/quickFixes/" + getClass().getSimpleName();
  }

  protected void doQuickFixTest(final Class inspectionClass, final String hint, LanguageLevel languageLevel) {
    runWithLanguageLevel(languageLevel, () -> doQuickFixTest(inspectionClass, hint));
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
