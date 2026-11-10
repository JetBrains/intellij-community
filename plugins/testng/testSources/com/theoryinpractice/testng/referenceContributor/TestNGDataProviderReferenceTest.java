// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.referenceContributor;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class TestNGDataProviderReferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package org.testng.annotations;
                         public @interface DataProvider {
                           String name() default "";
                         }""");
    myFixture.addClass("""
                         package org.testng.annotations;
                         public @interface Test {
                           String dataProvider() default "";
                           Class dataProviderClass() default Object.class;
                         }""");
    myFixture.addClass("""
                         package org.testng.internal;
                         public final class Version {
                           public static final String VERSION = "7.0.0";
                         }""");
  }

  @Test
  public void testResolveToSameClassProviderByMethodName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("TestClass.java",
                              """
                                import org.testng.annotations.*;
                                class TestClass {
                                  @DataProvider
                                  public Object[][] data() { return null; }
                              
                                  @Test(dataProvider = "dat<caret>a")
                                  public void testMe(String param) {}
                                }""");
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      PsiElement resolved = reference.resolve();
      assertNotNull(resolved);
      assertInstanceOf(PsiMethod.class, resolved);
      assertEquals("TestClass#data", getMethod(resolved));
    });
  }

  @Test
  public void testResolveToSameClassProviderByNameAttribute() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("TestClass.java",
                              """
                                import org.testng.annotations.*;
                                class TestClass {
                                  @DataProvider(name = "myData")
                                  public Object[][] providerMethod() { return null; }
                              
                                  @Test(dataProvider = "myDa<caret>ta")
                                  public void testMe(String param) {}
                                }""");
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      PsiElement resolved = reference.resolve();
      assertNotNull(resolved);
      assertInstanceOf(PsiMethod.class, resolved);
      assertEquals("TestClass#providerMethod", getMethod(resolved));
    });
  }

  @Test
  public void testResolveToExternalClassViaMethodAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         import org.testng.annotations.*;
                         final class ExternalProvider {
                           @DataProvider(name = "myData")
                           public static Object[][] data() { return null; }
                         }""");
    myFixture.configureByText("TestClass.java",
                              """
                                import org.testng.annotations.*;
                                class TestClass {
                                  @Test(dataProvider = "myDat<caret>a", dataProviderClass = ExternalProvider.class)
                                  public void testMe(String param) {}
                                }""");
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      PsiElement resolved = reference.resolve();
      assertNotNull(resolved);
      assertInstanceOf(PsiMethod.class, resolved);
      assertEquals("ExternalProvider#data", getMethod(resolved));
    });
  }

  @Test
  public void testResolveToExternalClassViaClassLevelAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         import org.testng.annotations.*;
                         final class ExternalProvider {
                           @DataProvider(name = "myData")
                           public static Object[][] data() { return null; }
                         }""");
    myFixture.configureByText("TestClass.java",
                              """
                                import org.testng.annotations.*;
                                @Test(dataProviderClass = ExternalProvider.class)
                                class TestClass {
                                  @Test(dataProvider = "myDat<caret>a")
                                  public void testMe(String param) {}
                                }""");
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      PsiElement resolved = reference.resolve();
      assertNotNull(resolved);
      assertInstanceOf(PsiMethod.class, resolved);
      assertEquals("ExternalProvider#data", getMethod(resolved));
    });
  }

  @Test
  public void testResolveToExternalClassViaSuperClassLevelAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         import org.testng.annotations.*;
                         final class ExternalProvider {
                           @DataProvider(name = "myData")
                           public static Object[][] data() { return null; }
                         }""");
    myFixture.addClass("""
                         import org.testng.annotations.*;
                         @Test(dataProviderClass = ExternalProvider.class)
                         abstract class BaseTest {}""");
    myFixture.configureByText("SubTest.java",
                              """
                                import org.testng.annotations.*;
                                class SubTest extends BaseTest {
                                  @Test(dataProvider = "myDat<caret>a")
                                  public void testMe(String param) {}
                                }""");
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      PsiElement resolved = reference.resolve();
      assertNotNull(resolved);
      assertEquals("ExternalProvider#data", getMethod(resolved));
    });
  }

  @Test
  public void testResolveToExternalClassViaInheritedClassLevelAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         import org.testng.annotations.*;
                         final class ExternalFirstProvider {
                           @DataProvider(name = "myData")
                           public static Object[][] data1() { return null; }
                         }""");
    myFixture.addClass("""
                         import org.testng.annotations.*;
                         final class ExternalSecondProvider {
                           @DataProvider(name = "myData")
                           public static Object[][] data2() { return null; }
                         }""");

    myFixture.addClass("""
                         import org.testng.annotations.*;
                         @Test(dataProviderClass = ExternalFirstProvider.class)
                         class FirstTest extends SuperTest {}""");
    myFixture.addClass("""
                         import org.testng.annotations.*;
                         @Test(dataProviderClass = ExternalSecondProvider.class)
                         class SecondTest extends SuperTest {}""");
    myFixture.configureByText("SubTest.java",
                              """
                                import org.testng.annotations.*;
                                abstract class SuperTest {
                                  @Test(dataProvider = "myDat<caret>a")
                                  public void testMe(String param) {}
                                }""");
    ReadAction.runBlocking(() -> {
      PsiPolyVariantReference reference =
        (PsiPolyVariantReference)myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      PsiElement resolved = reference.resolve();
      assertNull(resolved);
      ResolveResult[] results = reference.multiResolve(false);
      Set<String> methods = Arrays.stream(results).map(r -> getMethod(r.getElement())).collect(Collectors.toSet());
      assertEquals(Set.of("ExternalFirstProvider#data1", "ExternalSecondProvider#data2"), methods);
    });
  }

  @Test
  public void testNoResolveForUnknownProvider() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("TestClass.java",
                              """
                                import org.testng.annotations.*;
                                class TestClass {
                                  @Test(dataProvider = "nonexiste<caret>nt")
                                  public void testMe(String param) {}
                                }""");
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      assertNull(reference.resolve());
    });
  }

  private static String getMethod(PsiElement method) {
    assertInstanceOf(PsiMethod.class, method);
    return ((PsiMethod)method).getContainingClass().getName() + "#" + ((PsiMethod)method).getName();
  }
}
