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
public class DelegateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  @LombokParsingTest
  public void testDelegate$DelegateAlreadyImplemented() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateGenerics() {
    parsing.get().doTest(true);
  }

  public void ignore_testDelegate$DelegateOnGetter() {
    //TODO known problem, try to fix later
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateOnGetterNone() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateOnMethods() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateRecursion() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateTypesAndExcludes() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateWithDeprecated() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateWithException() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateWithVarargs() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateWithVarargs2() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateGenericInterfaceIssue88() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testDelegate$DelegateWithInterfaces() {
    parsing.get().doTest(true);
  }
}
