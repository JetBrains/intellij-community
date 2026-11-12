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
public class AccessorsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokConfigSystemFixture(projectFixture, "/configsystem/accessors", new LombokParsingOptions().compareAnnotations(true));

  @LombokParsingTest
  public void testChain$GetterSetterFieldTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testChain$GetterSetterFieldAnnotationOverwriteTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testChain$GetterSetterClassTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testChain$GetterSetterClassAnnotationOverwriteTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testChain$GetterSetterWithoutAccessorsAnnotationTest() {
    parsing.get().doTest();
  }

  /// /////////

  @LombokParsingTest
  public void testFluent$GetterSetterFieldTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFluent$GetterSetterFieldAnnotationOverwriteTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFluent$GetterSetterClassTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFluent$GetterSetterClassAnnotationOverwriteTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testFluent$GetterSetterWithoutAccessorsAnnotationTest() {
    parsing.get().doTest();
  }

  /// /////////

  @LombokParsingTest
  public void testPrefix$GetterSetterClassTest() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testPrefix$GetterSetterWithoutAccessorsAnnotationClassTest() {
    parsing.get().doTest();
  }

  /// /////////
  @LombokParsingTest
  public void testEmptyprefix$AccessorsWithEmptyPrefix() {
    parsing.get().doTest();
  }

  /// /////////
  @LombokParsingTest
  public void testMakefinal$AccessorsMakeFinalLombokConfig() {
    parsing.get().doTest();
  }

  /// /////////

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderWithPrefixes() {
    parsing.get().doTest();
  }

  /// /////////
  @LombokParsingTest
  public void testCapitalization$BuilderWithJavaBeansSpecCapitalization() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCapitalization$GetterWithJavaBeansSpecCapitalization() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCapitalization$SetterWithJavaBeansSpecCapitalization() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCapitalization$ValueWithJavaBeansSpecCapitalization() {
    parsing.get().doTest();
  }

  @LombokParsingTest
  public void testCapitalization$WithWithJavaBeansSpecCapitalization() {
    parsing.get().doTest();
  }
}
