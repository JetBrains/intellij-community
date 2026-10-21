package com.jetbrains.performanceScripts.lang;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

@TestApplication
public class IJPerfParserTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture = parsingFixture(
    projectFixture,
    PathManager.getCommunityHomePath() + TestUtil.getDataSubPath("") + "/parser",
    IJPerfFileType.DEFAULT_EXTENSION,
    new IJPerfParserDefinition()
  );

  @Test
  public void testCommandWithoutOptions() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommandWithSimpleOption() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommandWithSeveralOptions() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommandWithSeveralOptionsWithValue() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommandWithSeveralOptionsSeparated() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommandWithNumberOptions() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommandWithPipeOptionSeparator() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommandWithSimpleOptionWithValue() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testSimpleExample() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommandWithFilePathInParameters() {
    parsingFixture.get().doTest(true);
  }
}
