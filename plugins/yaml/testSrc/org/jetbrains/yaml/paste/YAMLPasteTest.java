// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.paste;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * Test methods have format: <testname>_<suffix>.
 * Suffix is used to distinguish tests with the same `before` and `after` states but with different insertion (with and without indent).
 */
public abstract class YAMLPasteTest extends BasePlatformTestCase {
  private static final String ZERO_INDENT_SAMPLE = "key1:\n" +
                                                   "  subKey: val1\n" +
                                                   "\n" +
                                                   "key2: val2";

  private static final String INDENTED_SAMPLE = "  key1:\n" +
                                                "    subKey: val1\n" +
                                                "\n" +
                                                "  key2: val2";

  private static final String EMPTY_LINES_SAMPLE = "\n" +
                                                   "\n" +
                                                   "\n";

  private final int myReformatOnPaste;

  private int myDefaultReformatOnPaste = CodeInsightSettings.INDENT_EACH_LINE;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  YAMLPasteTest(int paste) {
    myReformatOnPaste = paste;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultReformatOnPaste = CodeInsightSettings.getInstance().REFORMAT_ON_PASTE;
    CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = myReformatOnPaste;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = myDefaultReformatOnPaste;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/paste/data/";
  }

  public void testOneLinePaste() {
    doTest(" middle");
  }

  public void testPasteValue_zeroIndent() {
    doTest(ZERO_INDENT_SAMPLE);
  }

  public void testPasteValue_indented() {
    doTest(INDENTED_SAMPLE);
  }

  public void testPasteValue_empty() {
    doTest(EMPTY_LINES_SAMPLE, ".empty");
  }

  public void testPasteLines() {
    doTest(INDENTED_SAMPLE);
  }

  public void testPasteItem_zeroIndent() {
    doTest(ZERO_INDENT_SAMPLE);
  }

  public void testPasteItem_indented() {
    doTest(INDENTED_SAMPLE);
  }

  public void testPasteItem_empty() {
    doTest(EMPTY_LINES_SAMPLE, ".empty");
  }

  public void testPasteWithReplace_zeroIndent() {
    doInsertTest(ZERO_INDENT_SAMPLE);
  }

  public void testPasteWithReplace_indented() {
    doInsertTest(INDENTED_SAMPLE);
  }

  public void testPasteAsPlainAfterValue() {
    doTest("\n" + ZERO_INDENT_SAMPLE);
  }

  public void testPasteAsPlainInsideValue() {
    doTest("\n" + ZERO_INDENT_SAMPLE);
  }

  public void testPasteValueBeforeKey_zeroIndent() {
    doTest(ZERO_INDENT_SAMPLE + "\n");
  }

  public void testPasteValueBeforeKey_indented() {
    doTest(INDENTED_SAMPLE + "\n");
  }

  public void testPasteValueInsideIndent_zeroIndent() {
    doTest(ZERO_INDENT_SAMPLE + "\n");
  }

  public void testPasteValueInsideIndent_indented() {
    doTest(INDENTED_SAMPLE + "\n");
  }

  public void testPasteValueBeforeComment_zeroIndent() {
    doTest(ZERO_INDENT_SAMPLE);
  }

  public void testPasteValueBeforeComment_indented() {
    doTest(INDENTED_SAMPLE);
  }

  private void doTest(@NotNull String insert) {
    doTest(insert, "");
  }

  private void doInsertTest(@NotNull String insert) {
    doTest(insert, "", true);
  }

  private void doTest(@NotNull String insert, @NotNull String resultSuffix) {
    doTest(insert, resultSuffix, false);
  }

  private void doTest(@NotNull String insert, @NotNull String resultSuffix, boolean selectWord) {
    String testName = getTestName(true);
    String fileName = ObjectUtils.notNull(StringUtil.substringBefore(testName, "_"), testName);
    myFixture.configureByFile(fileName + ".before.yml");
    CopyPasteManager.getInstance().setContents(new StringSelection(insert));
    if (selectWord) {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    }
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResultByFile(fileName + resultSuffix + ".after.yml");
  }

  public static class YAMLPasteNoReformatTest extends YAMLPasteTest {
    public YAMLPasteNoReformatTest() {
      super(CodeInsightSettings.NO_REFORMAT);
    }
  }

  public static class YAMLPasteIndentBlockTest extends YAMLPasteTest {
    public YAMLPasteIndentBlockTest() {
      super(CodeInsightSettings.INDENT_BLOCK);
    }
  }

  public static class YAMLPasteIndentEachLineTest extends YAMLPasteTest {
    public YAMLPasteIndentEachLineTest() {
      super(CodeInsightSettings.INDENT_EACH_LINE);
    }
  }

  public static class YAMLPasteReformatBlockTest extends YAMLPasteTest {
    public YAMLPasteReformatBlockTest() {
      super(CodeInsightSettings.REFORMAT_BLOCK);
    }
  }
}
