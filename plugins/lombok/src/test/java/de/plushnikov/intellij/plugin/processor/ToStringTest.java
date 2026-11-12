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
public class ToStringTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  @LombokParsingTest
  public void testTostring$ToStringAutoExclude() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTostring$ToStringExplicitEmptyOf() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTostring$ToStringExplicitOfAndExclude() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTostring$ToStringExplicitInclude() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTostring$ToStringInner() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTostring$ToStringNewStyle() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTostring$ToStringPlain() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTostring$ToStringSimpleClassName() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTostring$ToStringWithNamedExistingMethods() {
    parsing.get().doTest(true);
  }
}
