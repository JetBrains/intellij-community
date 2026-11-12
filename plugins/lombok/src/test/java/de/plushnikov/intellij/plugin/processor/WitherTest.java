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
public class WitherTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokParsingFixture(projectFixture, "", new LombokParsingOptions().compareCodeBlocks(false));

  @LombokParsingTest
  public void testWither$WitherAccessLevel() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherAlreadyExists() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherAndAccessors() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherAndAllArgsConstructor() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherDeprecated() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherOnClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherOnStatic() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherPlain() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherWithDollar() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherWithGenerics() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testWither$WitherWithAbstract() {
    parsing.get().doTest(true);
  }
}
