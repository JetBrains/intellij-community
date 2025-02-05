// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.editing;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.formatter.YAMLCodeStyleSettings;

public class YAMLTypingTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/editing/data/";
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

  public void testBracket() {
    doTest("[");
  }

  public void testBrace() {
    doTest("{");
  }

  public void testBackspaceTopLevelBracket() {
    doBackspaceTest();
  }

  public void testBackspaceInternalBracket() {
    doBackspaceTest();
  }

  public void testBackspaceTopLevelBrace() {
    doBackspaceTest();
  }

  public void testBackspaceInternalBrace() {
    doBackspaceTest();
  }

  public void testRemoveHyphenOnEnterInTheMiddleItem() {
    doTest("\n");
  }

  public void testRemoveHyphenOnEnterInTheLastItem() {
    doTest("\n");
  }

  public void testDoNotInsertHyphen() {
    doTest("\n");
  }

  public void testAutoDecreaseHyphenIndent() {
    doTestForSettings("- ", false, true);
  }

  public void testAutoIncreaseHyphenIndent() {
    doTestForSettings("- ", true, true);
  }

  public void testDoNotChangeHyphenIndent1() {
    doTest("- ");
  }

  public void testDoNotChangeHyphenIndent2() {
    doTest("- ");
  }

  private void doTest(@NotNull String insert) {
    doTest(() -> myFixture.type(insert));
  }

  private void doBackspaceTest() {
    doTest(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE));
  }

  private void doTest(@NotNull Runnable actions) {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".before.yml");
    actions.run();
    myFixture.checkResultByFile(testName + ".after.yml");
  }

  private void doTestForSettings(@NotNull String insert, boolean indentSequenceVal, boolean autoHyphen) {
    String testName = getTestName(true);
    String fileName = ObjectUtils.notNull(StringUtil.substringBefore(testName, "_"), testName);
    myFixture.configureByFile(fileName + ".before.yml");

    boolean backupIndentSequenceVal = getCustomSettings().INDENT_SEQUENCE_VALUE;
    getCustomSettings().INDENT_SEQUENCE_VALUE = indentSequenceVal;

    boolean backupAutoHyphen = getCustomSettings().AUTOINSERT_SEQUENCE_MARKER;
    getCustomSettings().AUTOINSERT_SEQUENCE_MARKER = autoHyphen;

    myFixture.type(insert);
    myFixture.checkResultByFile(fileName + ".after.yml");

    getCustomSettings().INDENT_SEQUENCE_VALUE = backupIndentSequenceVal;
    getCustomSettings().AUTOINSERT_SEQUENCE_MARKER = backupAutoHyphen;
  }

  @NotNull
  private YAMLCodeStyleSettings getCustomSettings() {
    return CodeStyle.getCustomSettings(myFixture.getFile(), YAMLCodeStyleSettings.class);
  }
}
