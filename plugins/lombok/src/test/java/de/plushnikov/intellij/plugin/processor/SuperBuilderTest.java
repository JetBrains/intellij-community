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
 * Unit tests for @SuperBuilder support
 */
@TestApplication
public class SuperBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing =
    lombokParsingFixture(projectFixture, "", new LombokParsingOptions().annotationToComparePattern("java.lang.Deprecated"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = parsing.get().getFixture();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderAbstract() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderAbstractToBuilder() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderBasic() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderBasicToBuilder() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderCustomized() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderToBuilderOverride() {
    parsing.get().doTest(true);
  }

  //TODO Implement AnnotatedTypes handling/delombok
//  public void testSuperbuilder$SuperBuilderSingularAnnotatedTypes() {
//    doTest(true);
//  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderWithCustomBuilderMethod() {
    parsing.get().doTest(true);
  }

// TODO Implement defaults handling/delombok
//  public void testSuperbuilder$SuperBuilderWithDefaults() {
//    doTest(true);
//  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderWithGenerics() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderWithGenerics2() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderWithGenericsAndToBuilder() {
    parsing.get().doTest(true);
  }

  // TODO Implement NonNull handling/delombok
//  public void testSuperbuilder$SuperBuilderWithNonNull() {
//    doTest(true);
//  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderWithSupportClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$SuperBuilderWithDefinedBasicConstructor() {
    parsing.get().doTest(true);
  }

}
