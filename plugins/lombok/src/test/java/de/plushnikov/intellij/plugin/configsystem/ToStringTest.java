package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingOptions;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokConfigSystemFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
@TestApplication
public class ToStringTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokConfigSystemFixture(projectFixture, "/configsystem/tostring", new LombokParsingOptions().compareAnnotations(true));

  @LombokParsingTest
  public void testConfiguration$ToStringConfiguration() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDoNotUseGetters$SomeTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDoNotUseGetters$AnnotationOverwriteTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testIncludeFieldNames$SomeTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testIncludeFieldNames$AnnotationOverwriteTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCallSuper$AnnotationOverwriteTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCallSuper$SomeTestWithSuper() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCallSuper$SomeTestWithoutSuper() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testOnlyExplicitlyIncluded$SomeTest() {
    parsing.get().doTest();
  }
}
