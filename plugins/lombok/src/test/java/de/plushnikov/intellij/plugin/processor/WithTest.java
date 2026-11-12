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
public class WithTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokParsingFixture(projectFixture, "", new LombokParsingOptions().compareCodeBlocks(false));

  @LombokParsingTest
  public void testWith$WithAlreadyExists() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithAndAllArgsConstructor() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WitherAccessLevel() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WitherLegacyStar() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithInnerAnnotation() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithMethodAbstract() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithMethodMarkedDeprecated() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithOnClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithOnStatic() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithPlain() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithWithDollar() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithWithGenerics() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithWithTypeAnnos() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWith$WithAndBuilderDefaultOnFieldAndValueOnClass() {
    parsing.get().doTest(true);
  }

}
