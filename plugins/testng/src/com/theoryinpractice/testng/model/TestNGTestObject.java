/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.theoryinpractice.testng.model;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;

import java.util.*;

public abstract class TestNGTestObject {

  public static final String[] GROUPS_CONFIGURATION = {BeforeGroups.class.getName(), AfterGroups.class.getName()};

  private static final Logger LOG = Logger.getInstance(TestNGTestObject.class);
  protected final TestNGConfiguration myConfig;

  public TestNGTestObject(TestNGConfiguration config) {
    myConfig = config;
  }

  @NotNull
  public static TestNGTestObject fromConfig(@NotNull TestNGConfiguration config) {
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

    if (testObject.equals(TestType.SOURCE.getType())) {
      return new TestNGSource(config);
    }

    LOG.info("Unknown test object" + testObject);
    return new UnknownTestNGTestObject(config);
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
    calculateDependencies(methods, results, new LinkedHashSet<>(), searchScope, classes);
  }

  private static void calculateDependencies(final PsiMethod[] methods,
                                            final Map<PsiClass, Map<PsiMethod, List<String>>> results,
                                            final Set<PsiMember> alreadyMarkedToBeChecked,
                                            final GlobalSearchScope searchScope,
                                            @Nullable final PsiClass... classes) {
    if (classes != null && classes.length > 0) {
      final Set<PsiMember> membersToCheckNow = new LinkedHashSet<>();

      final Set<String> groupDependencies = new LinkedHashSet<>(), declaredGroups = new LinkedHashSet<>();
      final HashMap<String, Collection<String>> valuesMap = new HashMap<>();
      valuesMap.put("dependsOnGroups", groupDependencies);
      valuesMap.put("groups", declaredGroups);
      //find all mentioned groups and dependsOnGroup values
      TestNGUtil.collectAnnotationValues(valuesMap, methods, classes);

      if (!groupDependencies.isEmpty()) {
        collectGroupsMembers(TestNGUtil.TEST_ANNOTATION_FQN, groupDependencies, true, results, alreadyMarkedToBeChecked, searchScope, membersToCheckNow, classes);
      }

      if (!declaredGroups.isEmpty()) {
        for (String annotationFqn : GROUPS_CONFIGURATION) {
          collectGroupsMembers(annotationFqn, declaredGroups, false, results, alreadyMarkedToBeChecked, searchScope, membersToCheckNow, classes);
        }
      }

      collectDependsOnMethods(results, alreadyMarkedToBeChecked, membersToCheckNow, methods, classes);

      if (methods == null) {
        for (PsiClass c : classes) {
          results.put(c, new LinkedHashMap<>());
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

  private static void collectGroupsMembers(final String annotationFqn,
                                           final Set<String> groups,
                                           final boolean skipUnrelated,
                                           final Map<PsiClass, Map<PsiMethod, List<String>>> results,
                                           final Set<PsiMember> alreadyMarkedToBeChecked,
                                           final GlobalSearchScope searchScope,
                                           final Set<PsiMember> membersToCheckNow,
                                           final PsiClass... classes) {
    ApplicationManager.getApplication().runReadAction(() -> {
      final Project project = classes[0].getProject();
      final PsiClass testAnnotation = JavaPsiFacade.getInstance(project).findClass(annotationFqn, GlobalSearchScope.allScope(project));
      if (testAnnotation == null) {
        return;
      }
      for (PsiMember psiMember : AnnotatedMembersSearch.search(testAnnotation, searchScope)) {
        final PsiClass containingClass = psiMember.getContainingClass();
        if (containingClass == null) continue;
        if (skipUnrelated && ArrayUtil.find(classes, containingClass) < 0) continue;
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(psiMember, annotationFqn);
        if (TestNGUtil.isAnnotatedWithParameter(annotation, "groups", groups)) {
          if (appendMember(psiMember, alreadyMarkedToBeChecked, results)) {
            membersToCheckNow.add(psiMember);
          }
        }
      }
    });
  }

  private static void collectDependsOnMethods(final Map<PsiClass, Map<PsiMethod, List<String>>> results,
                                              final Set<PsiMember> alreadyMarkedToBeChecked,
                                              final Set<PsiMember> membersToCheckNow,
                                              final PsiMethod[] methods,
                                              final PsiClass... classes) {
    final PsiClass[] psiClasses;
    if (methods != null && methods.length > 0) {
      final Set<PsiClass> containingClasses = new LinkedHashSet<>();
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
      final Set<String> testMethodDependencies = new LinkedHashSet<>();
      final HashMap<String, Collection<String>> valuesMap = new HashMap<>();
      valuesMap.put("dependsOnMethods", testMethodDependencies);
      TestNGUtil.collectAnnotationValues(valuesMap, methods, containingClass);
      if (!testMethodDependencies.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(() -> {
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
      if (AnnotationUtil.isAnnotated(method, TestNGUtil.TEST_ANNOTATION_FQN, 0) &&
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
      psiMethods = new LinkedHashMap<>();
      results.put(psiClass, psiMethods);
      if (psiMember instanceof PsiClass) {
        result = underConsideration.add(psiMember);
      }
    }
    if (psiMember instanceof PsiMethod) {
      final boolean add = psiMethods.put((PsiMethod)psiMember, Collections.emptyList()) != null;
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
    final PsiMethod[] methods = ReadAction.compute(() -> psiClass.findMethodsByName(methodName, true));
    calculateDependencies(methods, classes, searchScope, psiClass);
    Map<PsiMethod, List<String>> psiMethods = classes.get(psiClass);
    if (psiMethods == null) {
      psiMethods = new LinkedHashMap<>();
      classes.put(psiClass, psiMethods);
    }
    for (PsiMethod method : methods) {
      if (!psiMethods.containsKey(method)) {
        psiMethods.put(method, Collections.emptyList());
      }
    }
  }

  private static class UnknownTestNGTestObject extends TestNGTestObject {
    public UnknownTestNGTestObject(TestNGConfiguration config) {
      super(config);
    }

    @Override
    public void fillTestObjects(Map<PsiClass, Map<PsiMethod, List<String>>> classes) {}

    @Override
    public String getGeneratedName() {
      return getActionName();
    }

    @Override
    public String getActionName() {
      return "Unknown";
    }

    @Override
    public void checkConfiguration() {}
  }
}
