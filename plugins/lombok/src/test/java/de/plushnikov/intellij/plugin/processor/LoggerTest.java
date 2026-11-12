package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingOptions;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokParsingFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
@TestApplication
public class LoggerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture, "", parsingOptions());

  /**
   * Returns the options of the fixture. The field initializer calls it, so an override must not read the fields of its class.
   */
  protected @NotNull LombokParsingOptions parsingOptions() {
    return new LombokParsingOptions();
  }

  @LombokParsingTest
  public void testLogger$LoggerCommons() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerJul() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerLog4j() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerLog4j2() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerSlf4j() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerSlf4jAlreadyExists() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerSlf4jOnNonType() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerSlf4jTypes() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerSlf4jWithPackage() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerXSlf4j() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerJBossLog() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testLogger$LoggerFlogger() {
    parsing.get().doTest(true);
  }

  // we cannot test CustomLog here because it requires config
}
