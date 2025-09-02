package com.intellij.java.lomboktest;

import com.intellij.java.codeInspection.DataFlowInspectionTest;
import com.intellij.java.codeInspection.DataFlowInspectionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class LombokDataFlowInspectionTest extends DataFlowInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_NEW_DESCRIPTOR;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/lombok/testData/inspection/dataflow";
  }

  public void testSpringNonNullApiOnPackage () {
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

    doTest();
  }

  @Override
  protected @NotNull String getTestFileName() {
    return "test/" + super.getTestFileName();
  }
}