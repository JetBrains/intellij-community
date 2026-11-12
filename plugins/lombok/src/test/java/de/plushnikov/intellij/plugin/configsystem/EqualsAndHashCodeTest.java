package de.plushnikov.intellij.plugin.configsystem;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingOptions;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;

import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokConfigSystemFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
@TestApplication
public class EqualsAndHashCodeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokConfigSystemFixture(projectFixture, "/configsystem/equalsandhashcode", new LombokParsingOptions().compareAnnotations(true));

  @LombokParsingTest
  public void testDoNotUseGetters$SomeTest() throws IOException {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDoNotUseGetters$AnnotationOverwriteTest() throws IOException {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCallSuper$WithSuperTest() throws IOException {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCallSuper$WithoutSuperTest() throws IOException {
    parsing.get().doTest();
  }
}
