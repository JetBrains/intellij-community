package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingOptions;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokParsingFixture;

@TestApplication
public class ValueTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokParsingFixture(projectFixture, "", new LombokParsingOptions().compareCodeBlocks(false));

  @LombokParsingTest
  public void testValue$ValueIssue78() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueIssue94() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValuePlain() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueStarImport() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueStaticConstructor() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueBuilder() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueAndBuilder93() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueAndWither() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueAndWitherAndRequiredConstructor() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueWithGeneric176() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueWithPackagePrivate() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testValue$ValueWithNonDefaultConstructor() {
    parsing.get().doTest(true);
  }
}
