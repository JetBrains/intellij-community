package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokParsingFixture;

@TestApplication
public class WithByTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  //public void testWithby$WithByInAnonymousClass() {
  //  doTest(true);
  //}

  @LombokParsingTest
  public void testWithby$WithByNullAnnos() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWithby$WithByOnRecord() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWithby$WithByOnRecordComponent() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWithby$WithByTypes() {
    parsing.get().doTest(true);
  }
}