package com.jetbrains.performanceScripts.lang;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NonNls;

public class IJPerfParserTest extends ParsingTestCase {

  public void testCommandWithoutOptions() {
    doTest(true);
  }

  public void testCommandWithSimpleOption() {
    doTest(true);
  }

  public void testCommandWithSeveralOptions() {
    doTest(true);
  }

  public void testCommandWithSeveralOptionsWithValue() {
    doTest(true);
  }

  public void testCommandWithSeveralOptionsSeparated() {
    doTest(true);
  }

  public void testCommandWithNumberOptions() {
    doTest(true);
  }

  public void testCommandWithPipeOptionSeparator() {
    doTest(true);
  }

  public void testCommandWithSimpleOptionWithValue() {
    doTest(true);
  }

  public void testSimpleExample() {
    doTest(true);
  }

  public void testCommandWithFilePathInParameters() {
    doTest(true);
  }

  public IJPerfParserTest() {
    super("parser", IJPerfFileType.DEFAULT_EXTENSION, new IJPerfParserDefinition());
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + TestUtil.getDataSubPath("");
  }
}
