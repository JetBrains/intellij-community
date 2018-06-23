// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.idea.Bombed;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.formatter.YAMLCodeStyleSettings;

import java.util.Calendar;
import java.util.function.Consumer;

/**
 * Format of the tests: test{AnswerName}_{source}
 * Answer files: {source}.{answerName}.txt
 * Source files: {source}.yml
 */
@SuppressWarnings("DefaultAnnotationParam")
public class YAMLFormattingTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/formatting/data/";
  }

  public void testSample_default() {
    doWholeFileTest();
  }

  public void testSample_indent4() {
    doWholeFileTest(() -> getCommonSettings().getIndentOptions().INDENT_SIZE = 4);
  }

  public void testSample_alignColon() {
    doWholeFileTest(() -> getCustomSettings().ALIGN_VALUES_PROPERTIES = YAMLCodeStyleSettings.ALIGN_ON_COLON);
  }

  //TODO: implement different align strategies
  public void testSample_alignValue() {
    doWholeFileTest(() -> getCustomSettings().ALIGN_VALUES_PROPERTIES = YAMLCodeStyleSettings.ALIGN_ON_VALUE);
  }

  public void testSample_indentSequenceValue() {
    doWholeFileTest(() -> getCustomSettings().INDENT_SEQUENCE_VALUE = true);
  }

  public void testSample_sequenceOnNewLine() {
    doWholeFileTest(() -> getCustomSettings().SEQUENCE_ON_NEW_LINE = true);
  }

  public void testSample_blockMappingOnNewLine() {
    doWholeFileTest(() -> getCustomSettings().BLOCK_MAPPING_ON_NEW_LINE = true);
  }

  public void testJsonStyle_default() {
    doWholeFileTest();
  }

  public void testSeveralDocuments_default() {
    doWholeFileTest();
  }

  public void testMapAsRoot() {
    doWholeFileTest();
  }

  public void testSecondItem_1() {
    doPartialReformatTest(2, 3);
  }

  public void testSecondItem_2() {
    doPartialReformatTest(6, 7);
  }

  public void testSecondItem_3() {
    doPartialReformatTest(10, 11);
  }

  // TODO: fix ticket RUBY-21789
  @Bombed(year = 2018, month = Calendar.JULY, day = 10, user = "alexey.merkulov",
    description = "Ticket RUBY-21789")
  public void testComments_default() {
    doWholeFileTest();
  }

  // TODO: fix ticket RUBY-21789
  @Bombed(year = 2018, month = Calendar.JULY, day = 10, user = "alexey.merkulov",
    description = "Ticket RUBY-21789")
  public void testComments_indentSequenceValue() {
    doWholeFileTest();
  }

  public void testRegression21787() {
    doWholeFileTest();
  }

  // TODO: fix ticket RUBY-21788
  @Bombed(year = 2018, month = Calendar.JULY, day = 10, user = "alexey.merkulov",
    description = "Ticket RUBY-21788")
  public void testParsingBug2() {
    doWholeFileTest();
  }

  @NotNull
  private CommonCodeStyleSettings getCommonSettings() {
    return CodeStyle.getLanguageSettings(myFixture.getFile(), YAMLLanguage.INSTANCE);
  }

  @NotNull
  private YAMLCodeStyleSettings getCustomSettings() {
    return CodeStyle.getCustomSettings(myFixture.getFile(), YAMLCodeStyleSettings.class);
  }

  private void doWholeFileTest() {
    doWholeFileTest(() -> {});
  }

  private void doWholeFileTest(Runnable configureOptions) {
    doCommonTest(configureOptions, codeStyleManager -> codeStyleManager.reformat(myFixture.getFile()));
  }

  /** @param end last line is not included into reformat */
  private void doPartialReformatTest(int startLine, int endLine) {
    doCommonTest(() -> {}, codeStyleManager -> {
      Editor editor = myFixture.getEditor();
      int start = editor.logicalPositionToOffset(new LogicalPosition(startLine, 0));
      int end   = editor.logicalPositionToOffset(new LogicalPosition(  endLine, 0));
      codeStyleManager.reformatRange(myFixture.getFile(), start, end);
    });
  }

  private void doCommonTest(@NotNull Runnable configureOptions, @NotNull Consumer<CodeStyleManager> reformat) {
    String testName = getTestName(true);
    int split = testName.indexOf('_');
    String source = split != -1 ? testName.substring(0, split) : testName;
    String resultName = testName.replace('_', '.');
    myFixture.configureByFile(source + ".yml");
    configureOptions.run();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
      reformat.accept(codeStyleManager);
    });
    myFixture.checkResultByFile(resultName + ".txt");
  }
}
