package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.intellij.lang.annotations.Language;

public class DeprecatedLombokAnnotationInspectionTest extends LombokInspectionTest {

  @Override
  protected InspectionProfileEntry getInspection() {
    return new DeprecatedLombokAnnotationInspection();
  }

  private void addOldClassDefinition(String className) {
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

  public void testDeprecatedBuilder() {
    final @Language("JAVA") String testClassText = """
      <error descr="Lombok's annotation 'lombok.experimental.Builder' is deprecated and not supported by lombok-plugin anymore. Use 'lombok.Builder' instead.">@lombok.experimental.Builder</error>
      public class DeprecationTest {
            private String someStr;
      }""";

    addOldClassDefinition("Builder");

    myFixture.configureByText("DeprecationTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  public void testDeprecatedValue() {
    final @Language("JAVA") String testClassText = """
      <error descr="Lombok's annotation 'lombok.experimental.Value' is deprecated and not supported by lombok-plugin anymore. Use 'lombok.Value' instead.">@lombok.experimental.Value</error>
      public class DeprecationTest {
            private String someStr;
      }""";
    addOldClassDefinition("Value");

    myFixture.configureByText("DeprecationTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  public void testDeprecatedWither() {
    final @Language("JAVA") String testClassText = """
      <error descr="Lombok's annotation 'lombok.experimental.Wither' is deprecated and not supported by lombok-plugin anymore. Use 'lombok.With' instead.">@lombok.experimental.Wither</error>
      public class DeprecationTest {
            private String someStr;
      }""";
    addOldClassDefinition("Wither");

    myFixture.configureByText("DeprecationTest.java", testClassText);
    myFixture.checkHighlighting();
  }

}

