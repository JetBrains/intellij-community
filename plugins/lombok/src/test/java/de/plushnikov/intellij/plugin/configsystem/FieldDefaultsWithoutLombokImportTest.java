package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokConfigSystemFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
@TestApplication
public class FieldDefaultsWithoutLombokImportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  //incomplete mode is not supported, because files don't contain any lombok annotations,
  //checking lombok.config is expensive, so skip such cases
  //after returning to normal mode, caches will be dropped
  private final TestFixture<LombokParsingFixture> parsing = lombokConfigSystemFixture(projectFixture, "/configsystem/fieldDefaults");

  @LombokParsingTest(incompleteMode = false)
  public void testDefaultFinal$DefaultFinalFieldTest() throws IOException {
    parsing.get().doTest();
  }

  @LombokParsingTest(incompleteMode = false)
  public void testDefaultPrivate$DefaultPrivateFieldTest() throws IOException {
    parsing.get().doTest();
  }
}
