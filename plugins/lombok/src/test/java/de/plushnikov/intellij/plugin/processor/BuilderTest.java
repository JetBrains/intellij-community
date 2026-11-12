package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingOptions;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.BeforeEach;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokParsingFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
@TestApplication
public class BuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokParsingFixture(projectFixture, "", new LombokParsingOptions().annotationToComparePattern("java.lang.Deprecated"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = parsing.get().getFixture();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  // This test is lombok's homepage example.
  @LombokParsingTest
  public void testBuilder$BuilderExample() {
    parsing.get().doTest(true);
  }

  // This test is lombok's homepage customized example.
  @LombokParsingTest
  public void testBuilder$BuilderExampleCustomized() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSimple() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderComplex() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithAccessors() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithAccessorsWithSetterPrefix() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithFieldAccessors() {
    parsing.get().doTest(true);
  }

  // This test is lombok's homepage example with predefined elements and another inner class.
  @LombokParsingTest
  public void testBuilder$BuilderPredefined() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithExistingBuilderClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderConstructorException() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderMultipleConstructorException() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderAndAllArgsConstructor() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderMethodException() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderValueData() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSingularGuavaListsSets() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSingularGuavaMaps() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSingularSets() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSingularSetsWithSetterPrefix() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSingularLists() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSingularMaps() {
    parsing.get().doTest(true);
  }

  // ignored because of disabled auto singularization
  public void ignore_testBuilder$BuilderSingularNoAuto() {
    parsing.get().doTest(true);
  }

  // ignored because of disabled guava redirection
  public void ignore_testBuilder$BuilderSingularRedirectToGuava() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderInstanceMethod() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSingularWithPrefixes() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$ObjectApiResponse() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderGenericsOnConstructor() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithDeprecatedField() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithDeprecatedParam() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithNoBuilderMethod() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderSimpleProtected() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithTolerate() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$MultipleMethodBuilderWithExistedClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithDefaultInnerFields() {
    // Initializer should be removed
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithSetterPrefixAndExistedMethods() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithXArgsConstructor() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithBuilderClassName() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithBuilderClassNameOnConstructor() {
    parsing.get().doTest(true);
  }

  // Verifies that builder class names start lowercase when the target class starts lowercase
  @LombokParsingTest
  public void testBuilder$BuilderWithLowercaseInnerClass() {
    parsing.get().doTest(true);
  }
}
