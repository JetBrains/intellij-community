// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lomboktest;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInsight.Nullability.NOT_NULL;
import static com.intellij.codeInsight.Nullability.NULLABLE;

public class LombokNonNullManagerTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  public void testTypeAnnotationNullabilityOnStubs() {
    List<String> notNulls = NullableNotNullManager.getInstance(getProject()).getNotNulls();
    assertTrue(notNulls.contains(LombokClassNames.NON_NULL));

    PsiClass clazz = myFixture.addClass("""
                                          import lombok.NonNull;
                                          public class NonNullTest {
                                              @NonNull
                                              private String test(@NonNull Integer param) {
                                                  return String.valueOf(param.hashCode());
                                              }}""");
    assertEquals(NOT_NULL, DfaPsiUtil.getTypeNullability(clazz.getMethods()[0].getReturnType()));
    assertEquals(NOT_NULL, DfaPsiUtil.getTypeNullability(clazz.getMethods()[0].getParameterList().getParameter(0).getType()));
  }

  public void testLombokAddNullAnnotationJavaxConfigProperty() {
    doAddNullAnnotationTest("javax");
  }

  public void testLombokAddNullAnnotationJetbrainsConfigProperty() {
    doAddNullAnnotationTest("jetbrains");
  }

  public void testLombokAddNullAnnotationCustomConfigProperty() {
    doAddNullAnnotationTest("custom:lombok.NonNull:org.jetbrains.annotations.Nullable");
  }

  public void testLombokAddNullAnnotationJspecifyConfigProperty() {
    doAddNullAnnotationTest("jspecify");
  }

  private void doAddNullAnnotationTest(String addNullAnnotation) {
    myFixture.addFileToProject("lombok.config", """
      lombok.addNullAnnotations = #PLACEHOLDER#
      config.stopBubbling = true
      """.replace("#PLACEHOLDER#", addNullAnnotation));

    myFixture.addClass("""
                         import lombok.Data;
                         import lombok.Builder;
                         import lombok.experimental.SuperBuilder;

                         public class App {
                             @Data
                             @Builder
                             public static class MyObjectB {
                                 private String myString;
                                 private String nonNullString;
                             }

                             @Data
                             @SuperBuilder
                             public static class MyObjectS {
                                 private String myString;
                                 private String nonNullString;
                             }

                             public static void main(final String[] args) {
                                 final MyObjectB myObjectB = MyObjectB.builder()
                                         .nonNullString("someString builder")
                                         .myString("myString builder")
                                         .build();
                                 System.out.println(myObjectB);

                                 final MyObjectS myObjectS = MyObjectS.builder()
                                         .nonNullString("someString super builder")
                                         .myString("myString super builder")
                                         .build();
                                 System.out.println(myObjectS);
                             }
                         }""");

    final PsiClass myObjectBClass = myFixture.findClass("App.MyObjectB");
    final PsiClass myObjectBBuilderClass = myFixture.findClass("App.MyObjectB.MyObjectBBuilder");

    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectBClass, "builder")));
    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectBClass, "toString")));

    assertEquals(NULLABLE, DfaPsiUtil.getElementNullability(null, findMethodParam(myObjectBClass, "equals", 0)));
    assertEquals(NULLABLE, DfaPsiUtil.getElementNullability(null, findMethodParam(myObjectBClass, "canEqual", 0)));

    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectBBuilderClass, "nonNullString")));
    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectBBuilderClass, "myString")));
    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectBBuilderClass, "build")));


    final PsiClass myObjectSClass = myFixture.findClass("App.MyObjectS");
    final PsiClass myObjectSBuilderClass = myFixture.findClass("App.MyObjectS.MyObjectSBuilder");

    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectSClass, "builder")));
    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectSClass, "toString")));

    assertEquals(NULLABLE, DfaPsiUtil.getElementNullability(null, findMethodParam(myObjectSClass, "equals", 0)));
    assertEquals(NULLABLE, DfaPsiUtil.getElementNullability(null, findMethodParam(myObjectSClass, "canEqual", 0)));

    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectSBuilderClass, "nonNullString")));
    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectSBuilderClass, "myString")));
    assertEquals(NOT_NULL, DfaPsiUtil.getElementNullability(null, findMethod(myObjectSBuilderClass, "build")));
  }

  private static PsiMethod findMethod(PsiClass myObjectBClass, String name) {
    return myObjectBClass.findMethodsByName(name, false)[0];
  }

  @Nullable
  private static PsiParameter findMethodParam(PsiClass myObjectBClass, String name, int parameterIndex) {
    return findMethod(myObjectBClass, name).getParameterList().getParameter(parameterIndex);
  }
}
