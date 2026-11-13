package com.intellij.java.lomboktest;

import com.intellij.java.codeInspection.DataFlowInspectionFixture;
import com.intellij.java.codeInspection.DataFlowInspectionTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class LombokDataFlowInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, LombokTestUtil.getTestDataPath() + "/inspection/dataflow", "test/");

  @Test
  public void testSpringNonNullApiOnPackage () {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("""
                         package org.springframework.lang;

                         import java.lang.annotation.Documented;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Retention;
                         import java.lang.annotation.RetentionPolicy;
                         import java.lang.annotation.Target;
                         import javax.annotation.Nonnull;
                         import javax.annotation.meta.TypeQualifierDefault;

                         @Target({ElementType.PACKAGE})
                         @Retention(RetentionPolicy.RUNTIME)
                         @Documented
                         @Nonnull
                         @TypeQualifierDefault({ElementType.METHOD, ElementType.PARAMETER})
                         public @interface NonNullApi {
                         }""");

    myFixture.addFileToProject("test/package-info.java", """
      @NonNullApi
      package test;

      import org.springframework.lang.NonNullApi;""");

    dataFlow.get().doTest();
  }
}