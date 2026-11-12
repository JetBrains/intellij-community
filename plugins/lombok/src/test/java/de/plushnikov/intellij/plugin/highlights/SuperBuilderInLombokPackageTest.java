package de.plushnikov.intellij.plugin.highlights;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

/**
 * Lombok 1.18.48 adds {@code lombok.SuperBuilder} next to {@code lombok.experimental.SuperBuilder}.
 */
@TestApplication
public class SuperBuilderInLombokPackageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testSuperBuilderInLombokPackage() {
    doTest("lombok.SuperBuilder");
  }

  @Test
  public void testSuperBuilderInExperimentalPackage() {
    doTest("lombok.experimental.SuperBuilder");
  }

  private void doTest(String annotationName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") String text = """
      import %s;

      @SuperBuilder
      class Parent {
        private String name;
      }

      @SuperBuilder
      class Child extends Parent {
        private int age;

        static Child create() {
          return Child.builder().name("name").age(1).build();
        }
      }
      """.formatted(annotationName);
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    myFixture.checkHighlighting();
  }
}
