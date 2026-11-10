// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.CantRunException;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.theoryinpractice.testng.TestNGFramework;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGTestObject;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.ui.actions.RerunFailedTestsAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class DetectClassesToRunTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.testng.annotations; @interface Test {String[] dependsOnMethods() default {};}");
    myFixture.addClass("package org.testng.annotations; @interface BeforeClass {}");
    myFixture.addClass("package org.testng.annotations; @interface BeforeGroups { String[] value() default {};}");
  }

  @Test
  public void testNonRelatedBeforeClassIncluded() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass configClass = myFixture.addClass("package p; public class AConfig {@org.testng.annotations.BeforeClass public void setup(){}}");
    final PsiClass testClass = myFixture.addClass("package p; public class ATest {@org.testng.annotations.Test public void testOne(){}}");
    //should not be included in resulted xml
    myFixture.addClass("package p; public class BTest {}");

    doTestPackageConfiguration(configClass, testClass);
  }

  @Test
  public void testNonRelatedIncludedWhenConfigIsLocatedInSuperclassInAnotherPackage() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class AConfig {@org.testng.annotations.BeforeClass public void setup(){}}");
    final PsiClass emptyClassWithSuperConfig = myFixture.addClass("package p; import a.AConfig; public class BConfig extends AConfig {}");
    final PsiClass testClass = myFixture.addClass("package p; public class ATest {@org.testng.annotations.Test public void testOne(){}}");
    doTestPackageConfiguration(emptyClassWithSuperConfig, testClass);
  }

  @Test
  public void testBeforeClassIsIncludedIfRunOnlyOneMethod() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass aClass =
      myFixture.addClass("""
                           package a; public class AConfig {@org.testng.annotations.BeforeClass public void setup(){}
                           @org.testng.annotations.Test public void testOne(){}
                           }""");
    doTestMethodConfiguration(aClass, aClass.getMethods()[1]);
  }

  @Test
  public void testOneMethodWhenAnnotationIsOnBaseClassOnly() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; @org.testng.annotations.Test public abstract class BaseClass {}");
    final PsiClass aClass =
      myFixture.addClass("package a; public class ATest extends BaseClass {" +
                         "  public void testOne(){}\n" +
                         "}");
    doTestMethodConfiguration(aClass, aClass.getMethods());
  }

  @Test
  public void testPackagePrivateMethodWhenAnnotationIsOnClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("package a; /** @noinspection ALL*/ @org.testng.annotations.Test public class MyTestClass {void testOne(){}}");
    ReadAction.runBlocking(() -> assertFalse(new TestNGFramework().isTestMethod(aClass.getMethods()[0], false)));
  }

  @Test
  public void testClassWithSingleParameterConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("package a; @org.testng.annotations.Test " +
                                         "public class MyTestClass {" +
                                         "public MyTetClass(String defaultName){}\n" +
                                         " public void testOne(){}" +
                                         "}");
    Project project = myFixture.getProject();
    TestClassFilter classFilter = new TestClassFilter(GlobalSearchScope.projectScope(project), project, false, true);
    assertTrue(classFilter.isAccepted(aClass));
  }

  @Test
  public void testOneMethodWithDependencies() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass aClass =
      myFixture.addClass("""
                           package a; public class ATest {  @org.testng.annotations.Test
                             public void testTwo(){}
                              @org.testng.annotations.Test(dependsOnMethods="testTwo")
                             public void testOne(){}
                           }""");
    doTestMethodConfiguration(aClass, aClass.getMethods());
  }

  @Test
  public void testDependsOnGroupDontIncludeForeignClass() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass aClass =
      myFixture.addClass("""
                           package a; public class ATest {  @org.testng.annotations.Test(groups = { "g1" })
                             public void testTwo(){}
                              @org.testng.annotations.Test(dependsOnGroups = {"g1" })
                             public void testOne(){}
                           }""");
    myFixture.addClass("""
                         package a; public class ForeignTest {  @org.testng.annotations.Test(groups = { "g1" })
                           public void testForth(){}
                          }""");
    doTestClassConfiguration(aClass);
  }

  @Test
  public void testBeforeGroups() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass aClass =
       myFixture.addClass("""
                            package a; public class ATest {  @org.testng.annotations.Test(groups = { "g1" })
                              public void testOne(){}
                            }""");
    final PsiClass configClass = myFixture.addClass("""
                                                      package a; public class ConfigTest {  @org.testng.annotations.BeforeGroups(groups = { "g1" })
                                                        public void testTwo(){}
                                                       }""");
    doTestMethodConfiguration(aClass, configClass, configClass.getMethods()[0], aClass.getMethods());
  }

  @Test
  public void testRerunFailedTestWithDependency() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass aClass =
      myFixture.addClass("package a; public class ATest {" +
                         "  @org.testng.annotations.Test()\n" +
                         "  public void testTwo(){}\n " +
                         "  @org.testng.annotations.Test(dependsOnMethods = \"testTwo\")\n" +
                         "  public void testOne(String s){}\n" + //parameterized
                         "}");

    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
    classes.put(aClass, new HashMap<>());
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    final SMTestProxy testProxy = new SMTestProxy("testOne", false, "java:test://a.ATest.testOne[a]");
    testProxy.setLocator(new JavaTestLocator());
    ReadAction.runBlocking(() -> {
      RerunFailedTestsAction.includeFailedTestWithDependencies(classes, projectScope, myFixture.getProject(), testProxy);

      assertEquals(1, classes.size());
      final Map<PsiMethod, List<String>> params = classes.get(aClass);
      assertThat(params.keySet()).contains(aClass.getMethods());
      final List<String> paramsToRerun = params.get(aClass.findMethodsByName("testOne", false)[0]);
      assertEquals(1, paramsToRerun.size());
      assertThat(paramsToRerun).contains("a");
    });
  }

  @Test
  public void testRerunFailedParameterized() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @SuppressWarnings("TestNGDataProvider") final PsiClass aClass =
      myFixture.addClass("""
                           package a; import org.testng.annotations.DataProvider;
                           import org.testng.annotations.Test;

                           import static org.testng.Assert.assertEquals;

                           public class ATest {

                               @DataProvider
                               public Object[][] testData() {
                                   return new Object[][]{
                                           {1},
                                           {2},
                                   };
                               }

                               @Test(dataProvider = "testData")
                               public void test(int in) {
                                   assertEquals(in, 0);
                               }
                           }
                           """);

    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
    classes.put(aClass, new HashMap<>());
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    final SMTestProxy testProxy = new SMTestProxy("test", false, "java:test://a.ATest.test[0]");
    testProxy.setLocator(new JavaTestLocator());
    ReadAction.runBlocking(() -> {
      RerunFailedTestsAction.includeFailedTestWithDependencies(classes, projectScope, myFixture.getProject(), testProxy);

      final SMTestProxy testProxy2 = new SMTestProxy("test", false, "java:test://a.ATest.test[1]");
      testProxy2.setLocator(new JavaTestLocator());
      RerunFailedTestsAction.includeFailedTestWithDependencies(classes, projectScope, myFixture.getProject(), testProxy2);

      assertEquals(1, classes.size());
      final Map<PsiMethod, List<String>> params = classes.get(aClass);
      final PsiMethod[] tests = aClass.findMethodsByName("test", false);
      assertThat(params.keySet()).contains(tests);
      final List<String> paramsToRerun = params.get(tests[0]);
      assertEquals(2, paramsToRerun.size());
      assertThat(paramsToRerun).contains("0", "1");
    });
  }

  private void doTestMethodConfiguration(PsiClass aClass, PsiMethod... expectedMethods) throws CantRunException {
    doTestMethodConfiguration(aClass, null, null, expectedMethods);
  }

  private void doTestMethodConfiguration(PsiClass aClass, PsiClass secondaryClass, PsiMethod configMethod, PsiMethod... expectedMethods) throws CantRunException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final TestNGConfiguration configuration = new TestNGConfiguration("testOne", myFixture.getProject());
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.METHOD.getType();
    data.METHOD_NAME = "testOne";
    data.setScope(TestSearchScope.SINGLE_MODULE);
    ReadAction.runBlocking(() -> {
      configuration.setModule(data.setMainClass(aClass));

      final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
      assertNotNull(testObject);
      final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
      testObject.fillTestObjects(classes);
      assertThat(classes.keySet()).contains(aClass);
      final Map<PsiMethod, List<String>> methods = classes.get(aClass);
      assertThat(methods.keySet()).contains(expectedMethods);
      if (secondaryClass != null) {
        final Map<PsiMethod, List<String>> configMethods = classes.get(secondaryClass);
        assertNotNull(configMethods);
        assertTrue(configMethods.containsKey(configMethod));
      }
    });
  }

  private void doTestClassConfiguration(PsiClass aClass) throws CantRunException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final TestNGConfiguration configuration =
      new TestNGConfiguration("TestA", myFixture.getProject());
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.CLASS.getType();
    data.setScope(TestSearchScope.SINGLE_MODULE);
    ReadAction.runBlocking(() -> {
      configuration.setModule(data.setMainClass(aClass));

      final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
      assertNotNull(testObject);
      final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
      testObject.fillTestObjects(classes);
      assertThat(classes.keySet()).contains(aClass);
      assertEquals(1, classes.size());
    });
  }

  private void doTestPackageConfiguration(PsiClass... containingClasses) throws CantRunException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final TestNGConfiguration configuration = new TestNGConfiguration("p", myFixture.getProject());
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.PACKAGE.getType();
    data.PACKAGE_NAME = "p";
    data.setScope(TestSearchScope.WHOLE_PROJECT);

    final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
    assertNotNull(testObject);
    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
    ReadAction.runBlocking(() -> testObject.fillTestObjects(classes));
    assertThat(classes.keySet()).contains(containingClasses);

    for (PsiClass psiClass : containingClasses) {
      assertThat(classes.get(psiClass).keySet()).isNullOrEmpty();
    }
  }
}
