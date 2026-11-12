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
public class FieldDefaultsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokConfigSystemFixture(projectFixture, "/configsystem/fieldDefaults");

  @LombokParsingTest
  public void testDefaultFinal$DefaultFinalFieldWithFieldDefaultsTest() throws IOException {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDefaultFinal$DefaultFinalFieldWithNonFinalTest() throws IOException {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDefaultFinal$DefaultFinalFieldWithUnrelatedFieldDefaultsTest() throws IOException {
    parsing.get().doTest();
  }
  //endregion

  //region DefaultPrivate
  @LombokParsingTest
  public void testDefaultPrivate$DefaultPrivateFieldWithFieldDefaultsTest() throws IOException {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDefaultPrivate$DefaultPrivateFieldWithPackagePrivateTest() throws IOException {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDefaultPrivate$DefaultPrivateFieldWithUnrelatedFieldDefaultsTest() throws IOException {
    parsing.get().doTest();
  }
  //endregion
}
