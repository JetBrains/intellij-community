// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.paste;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * These tests check the paste if config keys sequence
 * <p/>
 * Note: some tests have different insert string to check regexp pattern
 */
public class YAMLKeyPasteTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/paste/data/";
  }

  public void testPasteKeysInStart1() {
    doTest("next.subKey:");
  }

  public void testPasteKeysInStart2() {
    doTest("next.subKey");
  }

  public void testPasteKeysInMiddle1() {
    doTest("next.subKey:  ");
  }

  public void testPasteKeysInMiddle2() {
    doTest("next.subKey");
  }

  public void testPasteKeysInMiddleWithAnchor() {
    doTest("next.subKey");
  }

  public void testPasteKeysInEnd() {
    doTest("next.subKey");
  }

  public void testPasteKeysAtEOF1() {
    doTest("next.subKey");
  }

  public void testPasteKeysAtEOF2() {
    doTest("next.subKey");
  }

  public void testPasteKeysAtEOF3() {
    doTest("next.subKey");
  }

  public void testPasteKeysAtEOF4() {
    doTest("next.subKey");
  }

  public void testDoNotPasteKeysInPlainScalar() {
    doTest("next.subKey");
  }

  public void testDoNotPasteKeysInsideKey() {
    doTest("next.subKey");
  }

  public void testDoNotPasteKeysWithBadPattern() {
    doTest("some strange text");
  }

  public void testDoNotPasteKeysWithBadPattern2() {
    doTest("some. strange. text");
  }

  // Ambiguity in dot splitting
  public void testDoNotPasteKeysWithBadPattern3() {
    doTest("some.strange..text");
  }

  public void testDoNotPasteArrayAsKeys() {
    doTest("[x.y]");
  }

  // It is disputable behaviour
  public void testDoNotPasteKeysForOneWord() {
    doTest("word");
  }

  public void testPasteKeysWithExistedPrefix() {
    doTest("next.subKey");
  }

  public void testPasteKeysWithNoChange() {
    doTest("next.subKey");
  }

  public void testDoNotPasteKeysInSequenceNode() {
    doTest("next.subKey");
  }

  // It is disputable behaviour
  public void testPasteKeysInEmptyKeyValue() {
    doTest("next.subKey");
  }

  // It is disputable behaviour
  public void testPasteKeysInEmptyKeyValue2() {
    doTest("next.subKey");
  }

  // It is disputable behaviour
  public void testPasteKeysWithStrangeSymbols1() {
    doTest("workspace{w1}/next.^sub[Key]*(%magic%)");
  }

  // It is disputable behaviour
  public void testPasteKeysWithLeadingDot() {
    doTest(".leading.subKey");
  }

  private void doTest(@NotNull String pasteText) {
    String testName = getTestName(true);
    String fileName = ObjectUtils.notNull(StringUtil.substringBefore(testName, "_"), testName);
    myFixture.configureByFile(fileName + ".before.yml");
    CopyPasteManager.getInstance().setContents(new StringSelection(pasteText));
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResultByFile(fileName + ".after.yml");
  }
}