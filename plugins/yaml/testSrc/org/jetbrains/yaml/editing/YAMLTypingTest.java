// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.editing;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.formatter.YAMLCodeStyleSettings;

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

  public void testNewIndentedSequenceItem_indentedSequence() {
    doTestForSettings("\n", true, false);
  }

  public void testNewIndentedSequenceItem_sameIndent() {
    doTestForSettings("\n", false, false);
  }

  public void testNewIndentedAutoHyphen_indentedSequence() {
    doTestForSettings("\n", true, true);
  }

  public void testNewIndentedAutoHyphen_sameIndent() {
    doTestForSettings("\n", false, true);
  }

  public void testNewSequenceItemZeroIndent_indentedSequence() {
    doTestForSettings("\n", true, false);
  }

  public void testNewSequenceItemZeroIndent_sameIndent() {
    doTestForSettings("\n", false, false);
  }

  public void testNewZeroIndentAutoHyphen_indentedSequence() {
    doTestForSettings("\n", true, true);
  }

  public void testNewZeroIndentAutoHyphen_sameIndent() {
    doTestForSettings("\n", false, true);
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

  public void testRegressionRuby21808() {
    doTestForSettings("\n", false, false);
  }

  public void testPreserveDedent() {
    doTest("\n");
  }

  public void testPreserveDedentAfterInlinedMap() {
    doTest("\n");
  }

  public void testRemoveHyphenOnEnterInTheMiddleItem() {
    doTest("\n");
  }

  public void testRemoveHyphenOnEnterInTheLastItem() {
    doTest("\n");
  }

  @SuppressWarnings("SameParameterValue")
  private void doTest(@NotNull String insert) {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".yml");
    myFixture.type(insert);
    myFixture.checkResultByFile(testName + ".txt");
  }

  @SuppressWarnings("SameParameterValue")
  private void doTestForSettings(@NotNull String insert, boolean indentSequenceVal, boolean autoHyphen) {
    String testName = getTestName(true);
    String fileName = ObjectUtils.notNull(StringUtil.substringBefore(testName, "_"), testName);
    myFixture.configureByFile(fileName + ".yml");

    getCustomSettings().INDENT_SEQUENCE_VALUE = indentSequenceVal;
    getCustomSettings().AUTOINSERT_SEQUENCE_MARKER = autoHyphen;
    myFixture.type(insert);
    myFixture.checkResultByFile(fileName + ".txt");
  }

  @NotNull
  private YAMLCodeStyleSettings getCustomSettings() {
    return CodeStyle.getCustomSettings(myFixture.getFile(), YAMLCodeStyleSettings.class);
  }
}
