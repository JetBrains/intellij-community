// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.CantRunException;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.theoryinpractice.testng.TestNGFramework;
import com.theoryinpractice.testng.model.TestClassFilter;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGTestObject;
import com.theoryinpractice.testng.model.TestType;
import com.theoryinpractice.testng.ui.actions.RerunFailedTestsAction;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DetectClassesToRunTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.testng.annotations; @interface Test {String[] dependsOnMethods() default {};}");
    myFixture.addClass("package org.testng.annotations; @interface BeforeClass {}");
    myFixture.addClass("package org.testng.annotations; @interface BeforeGroups { String[] value() default {};}");
  }

  public void testNonRelatedBeforeClassIncluded() throws Exception {
    final PsiClass configClass = myFixture.addClass("package p; public class AConfig {@org.testng.annotations.BeforeClass public void setup(){}}");
    final PsiClass testClass = myFixture.addClass("package p; public class ATest {@org.testng.annotations.Test public void testOne(){}}");
    //should not be included in resulted xml
    myFixture.addClass("package p; public class BTest {}");

    doTestPackageConfiguration(configClass, testClass);
  }

  public void testNonRelatedIncludedWhenConfigIsLocatedInSuperclassInAnotherPackage() throws Exception {
    myFixture.addClass("package a; public class AConfig {@org.testng.annotations.BeforeClass public void setup(){}}");
    final PsiClass emptyClassWithSuperConfig = myFixture.addClass("package p; import a.AConfig; public class BConfig extends AConfig {}");
    final PsiClass testClass = myFixture.addClass("package p; public class ATest {@org.testng.annotations.Test public void testOne(){}}");
    doTestPackageConfiguration(emptyClassWithSuperConfig, testClass);
  }

  public void testBeforeClassIsIncludedIfRunOnlyOneMethod() throws Exception {
    final PsiClass aClass =
      myFixture.addClass("""
                           package a; public class AConfig {@org.testng.annotations.BeforeClass public void setup(){}
                           @org.testng.annotations.Test public void testOne(){}
                           }""");
    doTestMethodConfiguration(aClass, aClass.getMethods()[1]);
  }

  public void testOneMethodWhenAnnotationIsOnBaseClassOnly() throws Exception {
    myFixture.addClass("package a; @org.testng.annotations.Test public abstract class BaseClass {}");
    final PsiClass aClass =
      myFixture.addClass("package a; public class ATest extends BaseClass {" +
                         "  public void testOne(){}\n" +
                         "}");
    doTestMethodConfiguration(aClass, aClass.getMethods());
  }

  public void testPackagePrivateMethodWhenAnnotationIsOnClass() {
    PsiClass aClass = myFixture.addClass("package a; /** @noinspection ALL*/ @org.testng.annotations.Test public class MyTestClass {void testOne(){}}");
    assertFalse(new TestNGFramework().isTestMethod(aClass.getMethods()[0], false));
  }

  public void testClassWithSingleParameterConstructor() {
    PsiClass aClass = myFixture.addClass("package a; @org.testng.annotations.Test " +
                                         "public class MyTestClass {" +
                                         "public MyTetClass(String defaultName){}\n" +
                                         " public void testOne(){}" +
                                         "}");
    Project project = getProject();
    TestClassFilter classFilter = new TestClassFilter(GlobalSearchScope.projectScope(project), project, false, true);
    assertTrue(classFilter.isAccepted(aClass));
  }

  public void testOneMethodWithDependencies() throws Exception {
    final PsiClass aClass =
      myFixture.addClass("""
                           package a; public class ATest {  @org.testng.annotations.Test
                             public void testTwo(){}
                              @org.testng.annotations.Test(dependsOnMethods="testTwo")
                             public void testOne(){}
                           }""");
    doTestMethodConfiguration(aClass, aClass.getMethods());
  }

  public void testDependsOnGroupDontIncludeForeignClass() throws Exception {
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

  public void testBeforeGroups() throws Exception {
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

  public void testRerunFailedTestWithDependency() {
    final PsiClass aClass =
      myFixture.addClass("package a; public class ATest {" +
                         "  @org.testng.annotations.Test()\n" +
                         "  public void testTwo(){}\n " +
                         "  @org.testng.annotations.Test(dependsOnMethods = \"testTwo\")\n" +
                         "  public void testOne(String s){}\n" + //parameterized
                         "}");

    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
    classes.put(aClass, new HashMap<>());
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(getProject());
    final SMTestProxy testProxy = new SMTestProxy("testOne", false, "java:test://a.ATest.testOne[a]");
    testProxy.setLocator(new JavaTestLocator());
    RerunFailedTestsAction.includeFailedTestWithDependencies(classes, projectScope, getProject(), testProxy);

    assertEquals(1, classes.size());
    final Map<PsiMethod, List<String>> params = classes.get(aClass);
    assertContainsElements(params.keySet(), aClass.getMethods());
    final List<String> paramsToRerun = params.get(aClass.findMethodsByName("testOne", false)[0]);
    assertEquals(1, paramsToRerun.size());
    assertContainsElements(paramsToRerun, "a");
  }

  public void testRerunFailedParameterized() {
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
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(getProject());
    final SMTestProxy testProxy = new SMTestProxy("test", false, "java:test://a.ATest.test[0]");
    testProxy.setLocator(new JavaTestLocator());
    RerunFailedTestsAction.includeFailedTestWithDependencies(classes, projectScope, getProject(), testProxy);

    final SMTestProxy testProxy2 = new SMTestProxy("test", false, "java:test://a.ATest.test[1]");
    testProxy2.setLocator(new JavaTestLocator());
    RerunFailedTestsAction.includeFailedTestWithDependencies(classes, projectScope, getProject(), testProxy2);

    assertEquals(1, classes.size());
    final Map<PsiMethod, List<String>> params = classes.get(aClass);
    final PsiMethod[] tests = aClass.findMethodsByName("test", false);
    assertContainsElements(params.keySet(), tests);
    final List<String> paramsToRerun = params.get(tests[0]);
    assertEquals(2, paramsToRerun.size());
    assertContainsElements(paramsToRerun, "0", "1");
  }

  private void doTestMethodConfiguration(PsiClass aClass, PsiMethod... expectedMethods) throws CantRunException {
    doTestMethodConfiguration(aClass, null, null, expectedMethods);
  }

  private void doTestMethodConfiguration(PsiClass aClass, PsiClass secondaryClass, PsiMethod configMethod, PsiMethod... expectedMethods) throws CantRunException {
    final TestNGConfiguration configuration = new TestNGConfiguration("testOne", getProject());
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.METHOD.getType();
    data.METHOD_NAME = "testOne";
    data.setScope(TestSearchScope.SINGLE_MODULE);
    configuration.setModule(data.setMainClass(aClass));

    final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
    assertNotNull(testObject);
    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
    testObject.fillTestObjects(classes);
    assertContainsElements(classes.keySet(), aClass);
    final Map<PsiMethod, List<String>> methods = classes.get(aClass);
    assertContainsElements(methods.keySet(), expectedMethods);
    if (secondaryClass != null) {
      final Map<PsiMethod, List<String>> configMethods = classes.get(secondaryClass);
      assertNotNull(configMethods);
      assertTrue(configMethods.containsKey(configMethod));
    }
  }

  private void doTestClassConfiguration(PsiClass aClass) throws CantRunException {
    final TestNGConfiguration configuration =
      new TestNGConfiguration("TestA", getProject());
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.CLASS.getType();
    data.setScope(TestSearchScope.SINGLE_MODULE);
    configuration.setModule(data.setMainClass(aClass));

    final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
    assertNotNull(testObject);
    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
    testObject.fillTestObjects(classes);
    assertContainsElements(classes.keySet(), aClass);
    assertEquals(1, classes.size());
  }

  private void doTestPackageConfiguration(PsiClass... containingClasses) throws CantRunException {
    final TestNGConfiguration configuration = new TestNGConfiguration("p", getProject());
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.PACKAGE.getType();
    data.PACKAGE_NAME = "p";
    data.setScope(TestSearchScope.WHOLE_PROJECT);

    final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
    assertNotNull(testObject);
    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<>();
    testObject.fillTestObjects(classes);
    assertContainsElements(classes.keySet(), containingClasses);

    for (PsiClass psiClass : containingClasses) {
      assertEmpty(classes.get(psiClass).keySet());
    }
  }
}
