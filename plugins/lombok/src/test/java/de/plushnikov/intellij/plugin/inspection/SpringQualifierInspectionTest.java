package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class SpringQualifierInspectionTest extends LombokInspectionTest {

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SpringQualifierCopyableLombokAnnotationInspection();
  }

  public void testWithConfiguration() {
    addQualifierClass();
    myFixture.addFileToProject("lombok.config", """
      lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier
      config.stopBubbling = true
      """);

    myFixture.configureByText("SomeRouterService.java", """
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
      }""");
    myFixture.checkHighlighting();
  }

  public void testWithoutConfiguration() {
    addQualifierClass();
    myFixture.configureByText("SomeRouterService.java", """
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
      }""");
    myFixture.checkHighlighting();
  }

  public void testNonValidTarget() {
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

