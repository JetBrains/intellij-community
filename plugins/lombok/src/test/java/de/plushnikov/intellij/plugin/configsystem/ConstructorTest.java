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
 * Unit tests for Constructor and @ConstructorProperties generation
 */
@TestApplication
public class ConstructorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokConfigSystemFixture(projectFixture, "/configsystem/constructor", new LombokParsingOptions().compareAnnotations(true));

  @LombokParsingTest
  public void testDefault$NoArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDefault$AllArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testDefault$RequiredArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testSuppressConstructorProperties$NoArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testSuppressConstructorProperties$AllArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testSuppressConstructorProperties$RequiredArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testAddConstructorProperties$NoArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testAddConstructorProperties$AllArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testAddConstructorProperties$RequiredArgsConstructorTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCopyableAnnotations$FooServiceTest() {
    parsing.get().doTest();
  }
}
