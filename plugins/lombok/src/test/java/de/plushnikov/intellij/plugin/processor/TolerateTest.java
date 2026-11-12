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
public class TolerateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  @LombokParsingTest
  public void testTolerate$TolerateTest() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testTolerate$WitherTolerateTest() {
    parsing.get().doTest(true);
  }
}
