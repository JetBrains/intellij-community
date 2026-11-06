package de.plushnikov.intellij.plugin.highlights;

import com.intellij.ide.highlighter.JavaFileType;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

/**
 * Lombok 1.18.48 adds {@code lombok.SuperBuilder} next to {@code lombok.experimental.SuperBuilder}.
 */
public class SuperBuilderInLombokPackageTest extends AbstractLombokLightCodeInsightTestCase {

  public void testSuperBuilderInLombokPackage() {
    doTest("lombok.SuperBuilder");
  }

  public void testSuperBuilderInExperimentalPackage() {
    doTest("lombok.experimental.SuperBuilder");
  }

  private void doTest(String annotationName) {
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
