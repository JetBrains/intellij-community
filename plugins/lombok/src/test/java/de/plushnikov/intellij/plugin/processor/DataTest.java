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

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
@TestApplication
public class DataTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokParsingFixture(projectFixture, "", new LombokParsingOptions().compareCodeBlocks(false));

  @LombokParsingTest
  public void testData$DataExtended() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataIgnore() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataOnEnum() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataOnLocalClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataPlain() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataWithGetter() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataWithGetterNone() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataStaticConstructor() {
    // Test for issue #9
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataStaticConstructor2() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataStaticConstructor3() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataWithGeneric176() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$Klasse663() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataAndBuilder() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataAndSuperBuilder() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testData$DataExtendsRecursive() {
    parsing.get().doTest(true);
  }
}
