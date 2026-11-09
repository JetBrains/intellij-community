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
public class SpringQualifierInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new SpringQualifierCopyableLombokAnnotationInspection(), "");

  private static final @Language("JAVA") String FILE_TEXT_WITHOUT_WARNING = """
    import lombok.NonNull;
    import lombok.RequiredArgsConstructor;
    import org.springframework.beans.factory.annotation.Qualifier;

    @RequiredArgsConstructor
    public class SomeRouterService {

    \t@Qualifier("someDestination1") @NonNull
    \tprivate final SomeDestination someDestination1;
    \t@Qualifier("someDestination2") @NonNull
    \tprivate final SomeDestination someDestination2;

    \tprivate static class SomeDestination {}
    }""";
  public static final @Language("JAVA") String FILE_TEXT_WITH_WARNING = """
    import lombok.NonNull;
    import lombok.RequiredArgsConstructor;
    import org.springframework.beans.factory.annotation.Qualifier;

    @RequiredArgsConstructor
    public class SomeRouterService {

    \t<warning descr="Lombok does not copy the annotation 'org.springframework.beans.factory.annotation.Qualifier' into the constructor">@Qualifier("someDestination1")</warning> @NonNull
    \tprivate final SomeDestination someDestination1;
    \t<warning descr="Lombok does not copy the annotation 'org.springframework.beans.factory.annotation.Qualifier' into the constructor">@Qualifier("someDestination2")</warning> @NonNull
    \tprivate final SomeDestination someDestination2;

    \tprivate static class SomeDestination {}
    }""";

  @Test
  public void testWithConfigurationWithBlanks() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    addQualifierClass();
    myFixture.addFileToProject("lombok.config", """
      lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier
      config.stopBubbling = true
      """);

    myFixture.configureByText("SomeRouterService.java", FILE_TEXT_WITHOUT_WARNING);
    myFixture.checkHighlighting();
  }

  @Test
  public void testWithConfigurationWithoutBlanks() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    addQualifierClass();
    myFixture.addFileToProject("lombok.config", """
      lombok.copyableAnnotations+=org.springframework.beans.factory.annotation.Qualifier
      config.stopBubbling=true
      """);

    myFixture.configureByText("SomeRouterService.java", FILE_TEXT_WITHOUT_WARNING);
    myFixture.checkHighlighting();
  }

  @Test
  public void testWithoutConfiguration() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    addQualifierClass();
    myFixture.configureByText("SomeRouterService.java", FILE_TEXT_WITH_WARNING);
    myFixture.checkHighlighting();
  }

  @Test
  public void testNonValidTarget() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    addQualifierClass();
    myFixture.configureByText("Foo.java", """
      import lombok.NonNull;
      import lombok.RequiredArgsConstructor;
      import org.springframework.beans.factory.annotation.Qualifier;
      @RequiredArgsConstructor
      public class Foo {
        Runnable r = new Runnable() {
          @Qualifier
          public void run() {
          }
        };
      }""");
    myFixture.checkHighlighting();
  }


  private void addQualifierClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
                         package org.springframework.beans.factory.annotation;

                         import java.lang.annotation.Documented;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Inherited;
                         import java.lang.annotation.Retention;
                         import java.lang.annotation.RetentionPolicy;
                         import java.lang.annotation.Target;

                         @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
                         @Retention(RetentionPolicy.RUNTIME)
                         @Inherited
                         @Documented
                         public @interface Qualifier {
                             String value() default "";
                         }""");
  }
}

