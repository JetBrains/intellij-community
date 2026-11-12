package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokParsingFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
@TestApplication
public class EqualsAndHashCodeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCode() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeAutoExclude() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeExplicitEmptyOf() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeExplicitOfAndExclude() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeExplicitInclude() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeNestedShadow() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeNewStyle() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeOfAndExclude() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeWithExistingMethods() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeWithGenericsOnInners() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeWithGenericsOnInnersInInterfaces() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeWithOnParam() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeWithSomeExistingMethods() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testEqualsandhashcode$EqualsAndHashCodeWithNamedExistingMethods() {
    parsing.get().doTest(true);
  }
}
