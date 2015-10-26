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
package com.theoryinpractice.testng.model;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.ArrayUtil;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class TestNGTestObject {
  private static final Logger LOG = Logger.getInstance("#" + TestNGTestObject.class.getName());
  protected final TestNGConfiguration myConfig;

  public TestNGTestObject(TestNGConfiguration config) {
    myConfig = config;
  }

  public static TestNGTestObject fromConfig(TestNGConfiguration config) {
    final String testObject = config.getPersistantData().TEST_OBJECT;
    if (testObject.equals(TestType.PACKAGE.getType())) {
      return new TestNGTestPackage(config);
    }
    if (testObject.equals(TestType.CLASS.getType())) {
      return new TestNGTestClass(config);
    }
    if (testObject.equals(TestType.METHOD.getType())) {
      return new TestNGTestMethod(config);
    }

    if (testObject.equals(TestType.GROUP.getType())) {
      return new TestNGTestGroup(config);
    }

    if (testObject.equals(TestType.PATTERN.getType())) {
      return new TestNGTestPattern(config);
    }

    if (testObject.equals(TestType.SUITE.getType())){
      return new TestNGTestSuite(config);
    }
    assert false : testObject;
    return null;
  }

  public abstract void fillTestObjects(final Map<PsiClass, Map<PsiMethod, List<String>>> classes) throws CantRunException;
  public abstract String getGeneratedName();
  public abstract String getActionName();
  public abstract void checkConfiguration() throws RuntimeConfigurationException;

  public boolean isConfiguredByElement(PsiElement element) {
    return false;
  }

  protected static void calculateDependencies(PsiMethod[] methods,
                                              final Map<PsiClass, Map<PsiMethod, List<String>>> results,
                                              GlobalSearchScope searchScope,
                                              @Nullable final PsiClass... classes) {
    calculateDependencies(methods, results, new LinkedHashSet<PsiMember>(), searchScope, classes);
  }

  private static void calculateDependencies(final PsiMethod[] methods,
                                            final Map<PsiClass, Map<PsiMethod, List<String>>> results,
                                            final Set<PsiMember> alreadyMarkedToBeChecked,
                                            final GlobalSearchScope searchScope,
                                            @Nullable final PsiClass... classes) {
    if (classes != null && classes.length > 0) {
      final Set<String> groupDependencies = new LinkedHashSet<String>();
      TestNGUtil.collectAnnotationValues(groupDependencies, "dependsOnGroups", methods, classes);
      final Set<PsiMember> membersToCheckNow = new LinkedHashSet<PsiMember>();
      if (!groupDependencies.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final Project project = classes[0].getProject();
            final PsiClass testAnnotation =
              JavaPsiFacade.getInstance(project).findClass(TestNGUtil.TEST_ANNOTATION_FQN, GlobalSearchScope.allScope(project));
            LOG.assertTrue(testAnnotation != null);
            for (PsiMember psiMember : AnnotatedMembersSearch.search(testAnnotation, searchScope)) {
              final PsiClass containingClass = psiMember.getContainingClass();
              if (containingClass == null) continue;
              if (ArrayUtil.find(classes, containingClass) < 0) continue;
              final PsiAnnotation annotation = AnnotationUtil.findAnnotation(psiMember, TestNGUtil.TEST_ANNOTATION_FQN);
              if (TestNGUtil.isAnnotatedWithParameter(annotation, "groups", groupDependencies)) {
                if (appendMember(psiMember, alreadyMarkedToBeChecked, results)) {
                  membersToCheckNow.add(psiMember);
                }
              }
            }
          }
        });
      }

      collectDependsOnMethods(results, alreadyMarkedToBeChecked, membersToCheckNow, methods, classes);

      if (methods == null) {
        for (PsiClass c : classes) {
          results.put(c, new LinkedHashMap<PsiMethod, List<String>>());
        }
      } else {
        for (PsiMember psiMember : membersToCheckNow) {
          PsiClass psiClass;
          PsiMethod[] meths = null;
          if (psiMember instanceof PsiMethod) {
            psiClass = psiMember.getContainingClass();
            meths = new PsiMethod[] {(PsiMethod)psiMember};
          } else {
            psiClass = (PsiClass)psiMember;
          }
          calculateDependencies(meths, results, alreadyMarkedToBeChecked, searchScope, psiClass);
        }
      }
    }
  }

  private static void collectDependsOnMethods(final Map<PsiClass, Map<PsiMethod, List<String>>> results,
                                              final Set<PsiMember> alreadyMarkedToBeChecked,
                                              final Set<PsiMember> membersToCheckNow,
                                              final PsiMethod[] methods,
                                              final PsiClass... classes) {
    final PsiClass[] psiClasses;
    if (methods != null && methods.length > 0) {
      final Set<PsiClass> containingClasses = new LinkedHashSet<PsiClass>();
      for (final PsiMethod method : methods) {
        containingClasses.add(ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
          @Override
          public PsiClass compute() {
            return method.getContainingClass();
          }
        }));
      }
      psiClasses = containingClasses.toArray(new PsiClass[containingClasses.size()]);
    } else {
      psiClasses = classes;
    }
    for (final PsiClass containingClass : psiClasses) {
      final Set<String> testMethodDependencies = new LinkedHashSet<String>();
      TestNGUtil.collectAnnotationValues(testMethodDependencies, "dependsOnMethods", methods, containingClass);
      if (!testMethodDependencies.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final Project project = containingClass.getProject();
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            for (String dependency : testMethodDependencies) {
              final String className = StringUtil.getPackageName(dependency);
              final String methodName = StringUtil.getShortName(dependency);
              if (StringUtil.isEmpty(className)) {
                checkClassMethods(methodName, containingClass, alreadyMarkedToBeChecked, membersToCheckNow, results);
              }
              else {
                final PsiClass aClass = psiFacade.findClass(className, containingClass.getResolveScope());
                if (aClass != null) {
                  checkClassMethods(methodName, aClass, alreadyMarkedToBeChecked, membersToCheckNow, results);
                }
              }
            }
          }
        });
      }
    }
  }

  private static void checkClassMethods(String methodName,
                                        PsiClass containingClass,
                                        Set<PsiMember> alreadyMarkedToBeChecked,
                                        Set<PsiMember> membersToCheckNow,
                                        Map<PsiClass, Map<PsiMethod, List<String>>> results) {
    final PsiMethod[] psiMethods = containingClass.findMethodsByName(methodName, true);
    for (PsiMethod method : psiMethods) {
      if (AnnotationUtil.isAnnotated(method, TestNGUtil.TEST_ANNOTATION_FQN, false) &&
          appendMember(method, alreadyMarkedToBeChecked, results)) {
        membersToCheckNow.add(method);
      }
    }
  }

  private static boolean appendMember(final PsiMember psiMember,
                                      final Set<PsiMember> underConsideration,
                                      final Map<PsiClass, Map<PsiMethod, List<String>>> results) {
    boolean result = false;
    final PsiClass psiClass = psiMember instanceof PsiClass ? ((PsiClass)psiMember) : psiMember.getContainingClass();
    Map<PsiMethod, List<String>> psiMethods = results.get(psiClass);
    if (psiMethods == null) {
      psiMethods = new LinkedHashMap<PsiMethod, List<String>>();
      results.put(psiClass, psiMethods);
      if (psiMember instanceof PsiClass) {
        result = underConsideration.add(psiMember);
      }
    }
    if (psiMember instanceof PsiMethod) {
      final boolean add = psiMethods.put((PsiMethod)psiMember, Collections.<String>emptyList()) != null;
      if (add) {
        return underConsideration.add(psiMember);
      }
      return false;
    }
    return result;
  }

  @NotNull
  protected GlobalSearchScope getSearchScope() {
    final TestData data = myConfig.getPersistantData();
    final Module module = myConfig.getConfigurationModule().getModule();
    if (data.TEST_OBJECT.equals(TestType.PACKAGE.getType())) {
      SourceScope scope = myConfig.getPersistantData().getScope().getSourceScope(myConfig);
      if (scope != null) {
        return scope.getGlobalSearchScope();
      }
    }
    else if (module != null) {
      return GlobalSearchScope.moduleWithDependenciesScope(module);
    }
    return GlobalSearchScope.projectScope(myConfig.getProject());
  }

  public static void collectTestMethods(Map<PsiClass, Map<PsiMethod, List<String>>> classes,
                                        final PsiClass psiClass,
                                        final String methodName,
                                        final GlobalSearchScope searchScope) {
    final PsiMethod[] methods = ApplicationManager.getApplication().runReadAction(
      new Computable<PsiMethod[]>() {
        public PsiMethod[] compute() {
          return psiClass.findMethodsByName(methodName, true);
        }
      }
    );
    calculateDependencies(methods, classes, searchScope, psiClass);
    Map<PsiMethod, List<String>> psiMethods = classes.get(psiClass);
    if (psiMethods == null) {
      psiMethods = new LinkedHashMap<PsiMethod, List<String>>();
      classes.put(psiClass, psiMethods);
    }
    for (PsiMethod method : methods) {
      if (!psiMethods.containsKey(method)) {
        psiMethods.put(method, Collections.<String>emptyList());
      }
    }
  }
}
