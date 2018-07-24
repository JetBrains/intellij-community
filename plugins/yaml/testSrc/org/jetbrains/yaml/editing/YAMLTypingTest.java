// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.editing;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public class YAMLTypingTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/editing/data/";
  }

  public void testEmptyValue() {
    doTest("\n");
  }

  public void testNewValue() {
    doTest("\n");
  }

  public void testEmptySequenceItem() {
    doTest("\n");
  }

  public void testNewSequenceItem() {
    doTest("\n");
  }

  public void testEmptyInlinedValue() {
    doTest("\n");
  }

  public void testBeginBlockScalar() {
    doTest("\n");
  }

  public void testContinueBlockScalar() {
    doTest("\n");
  }

  private void doTest(String insert) {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".yml");
    myFixture.type(insert);
    myFixture.checkResultByFile(testName + ".txt");
  }
}
