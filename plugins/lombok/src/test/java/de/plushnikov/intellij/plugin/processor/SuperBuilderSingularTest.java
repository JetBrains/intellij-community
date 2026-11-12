package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokParsingFixture;
import de.plushnikov.intellij.plugin.LombokParsingTest;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.BeforeEach;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokParsingFixture;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
@TestApplication
public class SuperBuilderSingularTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = parsing.get().getFixture();
    // Add fake classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableSet<E> extends java.util.SortedSet<E> {}");
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaBiMap() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaCollection() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaList() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaMap() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaSet() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaSortedMap() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaSortedSet() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Guava$SingularGuavaTable() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularCollection() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularIterable() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularList() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularNavigableSet() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularSet() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Collection$SingularSortedSet() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Map$SingularMap() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Map$SingularNavigableMap() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testSuperbuilder$Singular$Generic$Util$Map$SingularSortedMap() {
    parsing.get().doTest(true);
  }
}
