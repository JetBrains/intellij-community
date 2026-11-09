package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

@TestApplication
public class DeprecatedLombokAnnotationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new DeprecatedLombokAnnotationInspection(), "");

  private void addOldClassDefinition(String className) {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final @Language("JAVA") String template = """
      package lombok.experimental;

      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;

      @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
      @Retention(RetentionPolicy.RUNTIME)
      public @interface {className} {
          String value() default "";
      }""";
    myFixture.addClass(template.replace("{className}", className));
  }

  @Test
  public void testDeprecatedBuilder() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final @Language("JAVA") String testClassText = """
      <error descr="Lombok's annotation 'lombok.experimental.Builder' is deprecated and not supported by Lombok plugin anymore. Use 'lombok.Builder' instead.">@lombok.experimental.Builder</error>
      public class DeprecationTest {
            private String someStr;
      }""";

    addOldClassDefinition("Builder");

    myFixture.configureByText("DeprecationTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  @Test
  public void testDeprecatedValue() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final @Language("JAVA") String testClassText = """
      <error descr="Lombok's annotation 'lombok.experimental.Value' is deprecated and not supported by Lombok plugin anymore. Use 'lombok.Value' instead.">@lombok.experimental.Value</error>
      public class DeprecationTest {
            private String someStr;
      }""";
    addOldClassDefinition("Value");

    myFixture.configureByText("DeprecationTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  @Test
  public void testDeprecatedWither() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final @Language("JAVA") String testClassText = """
      <error descr="Lombok's annotation 'lombok.experimental.Wither' is deprecated and not supported by Lombok plugin anymore. Use 'lombok.With' instead.">@lombok.experimental.Wither</error>
      public class DeprecationTest {
            private String someStr;
      }""";
    addOldClassDefinition("Wither");

    myFixture.configureByText("DeprecationTest.java", testClassText);
    myFixture.checkHighlighting();
  }

}

