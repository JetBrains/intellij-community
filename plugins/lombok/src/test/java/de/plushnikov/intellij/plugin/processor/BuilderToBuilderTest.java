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
public class BuilderToBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokParsingFixture> parsing = lombokParsingFixture(projectFixture);

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = parsing.get().getFixture();
    // Add dummy classes, which are absent in mockJDK
    myFixture.addClass("package java.util;\n  public interface NavigableMap<K,V> extends java.util.SortedMap<K,V> {}");
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithToBuilder() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithToBuilderOnClass() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithToBuilderOnConstructor() {
    parsing.get().doTest(true);
  }

  @LombokParsingTest
  public void testBuilder$BuilderWithToBuilderOnMethod() {
    parsing.get().doTest(true);
  }

}
