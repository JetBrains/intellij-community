package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokConfigSystemFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
@TestApplication
public class LoggerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokConfigSystemFixture(projectFixture, "/configsystem/log");

  @LombokParsingTest
  public void testFieldIsStatic$LogTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldIsStatic$Log4jTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldIsStatic$Log4j2Test() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldIsStatic$CommonsLogTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldIsStatic$Slf4jTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldIsStatic$XSlf4jTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldIsStatic$JBossLogTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldIsStatic$Slf4jWithReqConstructor() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldName$LogTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldName$Log4jTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldName$Log4j2Test() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldName$CommonsLogTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldName$Slf4jTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldName$XSlf4jTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFieldName$JBossLogTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCustomSimple$CustomLogTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCustomComplex$CustomLogTest() {
    parsing.get().doTest();
  }
}
