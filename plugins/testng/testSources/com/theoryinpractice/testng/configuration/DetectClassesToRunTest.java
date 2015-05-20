/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.CantRunException;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGTestObject;
import com.theoryinpractice.testng.model.TestType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DetectClassesToRunTest extends LightCodeInsightFixtureTestCase {
  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.testng.annotations; @interface Test {public String[] dependsOnMethods() default {};}");
    myFixture.addClass("package org.testng.annotations; @interface BeforeClass {}");
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testNonRelatedBeforeClassIncluded() throws Exception {
    final PsiClass configClass = myFixture.addClass("package p; public class AConfig {@org.testng.annotations.BeforeClass public void setup(){}}");
    final PsiClass testClass = myFixture.addClass("package p; public class ATest {@org.testng.annotations.Test public void testOne(){}}");
    //should not be included in resulted xml
    myFixture.addClass("package p; public class BTest {}");

    doTestPackageConfiguration(configClass, testClass);
  }

  @Test
  public void testNonRelatedIncludedWhenConfigIsLocatedInSuperclassInAnotherPackage() throws Exception {
    myFixture.addClass("package a; public class AConfig {@org.testng.annotations.BeforeClass public void setup(){}}");
    final PsiClass emptyClassWithSuperConfig = myFixture.addClass("package p; import a.AConfig; public class BConfig extends AConfig {}");
    final PsiClass testClass = myFixture.addClass("package p; public class ATest {@org.testng.annotations.Test public void testOne(){}}");
    doTestPackageConfiguration(emptyClassWithSuperConfig, testClass);
  }

  @Test
  public void testBeforeClassIsIncludedIfRunOnlyOneMethod() throws Exception {
    final PsiClass aClass =
      myFixture.addClass("package a; public class AConfig {" +
                         "@org.testng.annotations.BeforeClass public void setup(){}\n" +
                         "@org.testng.annotations.Test public void testOne(){}\n" +
                         "}");
    doTestMethodConfiguration(aClass, aClass.getMethods()[1]);
  }

  @Test
  public void testOneMethodWhenAnnotationIsOnBaseClassOnly() throws Exception {
    myFixture.addClass("package a; @org.testng.annotations.Test public class BaseClass {}");
    final PsiClass aClass =
      myFixture.addClass("package a; public class ATest extends BaseClass {" +
                         "  public void testOne(){}\n" +
                         "}");
    doTestMethodConfiguration(aClass, aClass.getMethods());
  }

  @Test
  public void testOneMethodWithDependencies() throws Exception {
    final PsiClass aClass =
      myFixture.addClass("package a; public class ATest {" +
                         "  @org.testng.annotations.Test\n" +
                         "  public void testTwo(){}\n " +
                         "  @org.testng.annotations.Test(dependsOnMethods=\"testTwo\")\n" +
                         "  public void testOne(){}\n" +
                         "}");
    doTestMethodConfiguration(aClass, aClass.getMethods());
  }

  @Test
  public void testDependsOnGroupDontIncludeForeignClass() throws Exception {
    final PsiClass aClass =
      myFixture.addClass("package a; public class ATest {" +
                         "  @org.testng.annotations.Test(groups = { \"g1\" })\n" +
                         "  public void testTwo(){}\n " +
                         "  @org.testng.annotations.Test(dependsOnGroups = {\"g1\" })\n" +
                         "  public void testOne(){}\n" +
                         "}");
    myFixture.addClass("package a; public class ForeignTest {" +
                       "  @org.testng.annotations.Test(groups = { \"g1\" })\n" +
                       "  public void testForth(){}\n " +
                       "}");
    doTestClassConfiguration(aClass);
  }

  private void doTestMethodConfiguration(PsiClass aClass, PsiMethod... expectedMethods) throws CantRunException {
    final TestNGConfiguration configuration =
      new TestNGConfiguration("testOne", getProject(), TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.METHOD.getType();
    data.METHOD_NAME = "testOne";
    data.setScope(TestSearchScope.SINGLE_MODULE);
    configuration.setModule(data.setMainClass(aClass));

    final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
    assertNotNull(testObject);
    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>>();
    testObject.fillTestObjects(classes);
    assertContainsElements(classes.keySet(), aClass);
    final Map<PsiMethod, List<String>> methods = classes.get(aClass);
    assertContainsElements(methods.keySet(), expectedMethods);
  }
  
  private void doTestClassConfiguration(PsiClass aClass) throws CantRunException {
    final TestNGConfiguration configuration =
      new TestNGConfiguration("TestA", getProject(), TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.CLASS.getType();
    data.setScope(TestSearchScope.SINGLE_MODULE);
    configuration.setModule(data.setMainClass(aClass));

    final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
    assertNotNull(testObject);
    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>>();
    testObject.fillTestObjects(classes);
    assertContainsElements(classes.keySet(), aClass);
    assertEquals(1, classes.size());
  }

  private void doTestPackageConfiguration(PsiClass... containingClasses) throws CantRunException {
    final TestNGConfiguration configuration =
      new TestNGConfiguration("p", getProject(), TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
    final TestData data = configuration.getPersistantData();
    data.TEST_OBJECT = TestType.PACKAGE.getType();
    data.PACKAGE_NAME = "p";
    data.setScope(TestSearchScope.WHOLE_PROJECT);

    final TestNGTestObject testObject = TestNGTestObject.fromConfig(configuration);
    assertNotNull(testObject);
    final LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>> classes = new LinkedHashMap<PsiClass, Map<PsiMethod, List<String>>>();
    testObject.fillTestObjects(classes);
    assertContainsElements(classes.keySet(), containingClasses);

    for (PsiClass psiClass : containingClasses) {
      assertEmpty(classes.get(psiClass).keySet());
    }
  }
}
