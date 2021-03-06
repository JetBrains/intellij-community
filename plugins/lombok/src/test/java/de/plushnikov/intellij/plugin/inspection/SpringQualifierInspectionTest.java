package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class SpringQualifierInspectionTest extends LombokInspectionTest {

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SpringQualifierCopyableLombokAnnotationInspection();
  }

  public void testWithConfiguration() {
    addQualifierClass();
    myFixture.addFileToProject("lombok.config", "lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier\n" +
                                                "config.stopBubbling = true\n");

    myFixture.configureByText("SomeRouterService.java", "import lombok.NonNull;\n" +
                                                        "import lombok.RequiredArgsConstructor;\n" +
                                                        "import org.springframework.beans.factory.annotation.Qualifier;\n" +
                                                        "\n" +
                                                        "@RequiredArgsConstructor\n" +
                                                        "public class SomeRouterService {\n" +
                                                        "\n" +
                                                        "\t@Qualifier(\"someDestination1\") @NonNull\n" +
                                                        "\tprivate final SomeDestination someDestination1;\n" +
                                                        "\t@Qualifier(\"someDestination2\") @NonNull\n" +
                                                        "\tprivate final SomeDestination someDestination2;\n" +
                                                        "\n" +
                                                        "\tprivate static class SomeDestination {}\n" +
                                                        "}");
    myFixture.checkHighlighting();
  }

  public void testWithoutConfiguration() {
    addQualifierClass();
    myFixture.configureByText("SomeRouterService.java", "import lombok.NonNull;\n" +
                                                        "import lombok.RequiredArgsConstructor;\n" +
                                                        "import org.springframework.beans.factory.annotation.Qualifier;\n" +
                                                        "\n" +
                                                        "@RequiredArgsConstructor\n" +
                                                        "public class SomeRouterService {\n" +
                                                        "\n" +
                                                        "\t<warning descr=\"Lombok does not copy the annotation 'org.springframework.beans.factory.annotation.Qualifier' into the constructor\">@Qualifier(\"someDestination1\")</warning> @NonNull\n" +
                                                        "\tprivate final SomeDestination someDestination1;\n" +
                                                        "\t<warning descr=\"Lombok does not copy the annotation 'org.springframework.beans.factory.annotation.Qualifier' into the constructor\">@Qualifier(\"someDestination2\")</warning> @NonNull\n" +
                                                        "\tprivate final SomeDestination someDestination2;\n" +
                                                        "\n" +
                                                        "\tprivate static class SomeDestination {}\n" +
                                                        "}");
    myFixture.checkHighlighting();
  }

  public void testNonValidTarget() {
    addQualifierClass();
    myFixture.configureByText("Foo.java", "import lombok.NonNull;\n" +
                                                        "import lombok.RequiredArgsConstructor;\n" +
                                                        "import org.springframework.beans.factory.annotation.Qualifier;\n" +
                                                        "@RequiredArgsConstructor\n" +
                                                        "public class Foo {\n" +
                                                        "  Runnable r = new Runnable() {\n" +
                                                        "    @Qualifier\n" +
                                                        "    public void run() {\n" +
                                                        "    }\n" +
                                                        "  };\n" +
                                                        "}");
    myFixture.checkHighlighting();
  }


  private void addQualifierClass() {
    myFixture.addClass("package org.springframework.beans.factory.annotation;\n" +
                       "\n" +
                       "import java.lang.annotation.Documented;\n" +
                       "import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Inherited;\n" +
                       "import java.lang.annotation.Retention;\n" +
                       "import java.lang.annotation.RetentionPolicy;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "\n" +
                       "@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})\n" +
                       "@Retention(RetentionPolicy.RUNTIME)\n" +
                       "@Inherited\n" +
                       "@Documented\n" +
                       "public @interface Qualifier {\n" +
                       "    String value() default \"\";\n" +
                       "}");
  }
}

