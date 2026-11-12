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
public class SetterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  @LombokParsingTest
  public void testSetter$SetterAccessLevel() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSetter$SetterAlreadyExists() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSetter$SetterDeprecated() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSetter$SetterOnClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSetter$SetterOnMethodOnParam() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSetter$SetterOnStatic() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSetter$SetterPlain() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSetter$SetterWithDollar() {
    parsing.get().doTest(true);
  }
}
