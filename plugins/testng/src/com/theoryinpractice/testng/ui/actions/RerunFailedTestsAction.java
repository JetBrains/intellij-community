// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.CantRunException;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.actions.JavaRerunFailedTestsAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.configuration.SearchingForTestsTask;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationProducer;
import com.theoryinpractice.testng.configuration.TestNGRunnableState;
import com.theoryinpractice.testng.model.TestNGTestObject;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RerunFailedTestsAction extends JavaRerunFailedTestsAction {
  public RerunFailedTestsAction(@NotNull ComponentContainer componentContainer, @NotNull TestConsoleProperties consoleProperties) {
    super(componentContainer, consoleProperties);
  }

  @Override
  protected MyRunProfile getRunProfile(@NotNull ExecutionEnvironment environment) {
    final TestNGConfiguration configuration = (TestNGConfiguration)myConsoleProperties.getConfiguration();
    final List<AbstractTestProxy> failedTests = getFailedTests(configuration.getProject());
    return new MyRunProfile(configuration) {
      @Override
      public Module @NotNull [] getModules() {
        return configuration.getModules();
      }

      @Override
      public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
        return new TestNGRunnableState(env, configuration) {

          @Override
          public SearchForTestsTask createSearchingForTestsTask() {
            return createSearchingForTestsTask(new LocalTargetEnvironment(new LocalTargetEnvironmentRequest()));
          }

          @Override
          public SearchForTestsTask createSearchingForTestsTask(@NotNull TargetEnvironment targetEnvironment) {
            return new SearchingForTestsTask(getServerSocket(), getConfiguration(), myTempFile) {
              @Override
              protected void fillTestObjects(final Map<PsiClass, Map<PsiMethod, List<String>>> classes) throws CantRunException {
                final HashMap<PsiClass, Map<PsiMethod, List<String>>> fullClassList = new HashMap<>();
                super.fillTestObjects(fullClassList);
                for (final PsiClass aClass : fullClassList.keySet()) {
                  if (!ReadAction.compute(() -> TestNGUtil.hasTest(aClass))) {
                    classes.put(aClass, fullClassList.get(aClass));
                  }
                }

                final GlobalSearchScope scope = getConfiguration().getConfigurationModule().getSearchScope();
                final Project project = getConfiguration().getProject();
                for (final AbstractTestProxy proxy : failedTests) {
                  ApplicationManager.getApplication().runReadAction(() -> includeFailedTestWithDependencies(classes, scope, project, proxy));
                }
              }


            };
          }
        };
      }
    };
  }

  public static void includeFailedTestWithDependencies(Map<PsiClass, Map<PsiMethod, List<String>>> classes,
                                                       GlobalSearchScope scope,
                                                       Project project,
                                                       AbstractTestProxy proxy) {
    final Location location = proxy.getLocation(project, scope);
    if (location != null) {
      final PsiElement element = location.getPsiElement();
      if (element instanceof PsiMethod psiMethod && element.isValid()) {
        PsiClass psiClass = psiMethod.getContainingClass();
        if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          final AbstractTestProxy parent = proxy.getParent();
          final PsiElement elt = parent != null ? parent.getLocation(project, scope).getPsiElement() : null;
          if (elt instanceof PsiClass) {
            psiClass = (PsiClass)elt;
          }
        }
        TestNGTestObject.collectTestMethods(classes, psiClass, psiMethod.getName(), scope);
        Map<PsiMethod, List<String>> psiMethods = classes.get(psiClass);
        if (psiMethods == null) {
          psiMethods = new LinkedHashMap<>();
          classes.put(psiClass, psiMethods);
        }
        List<String> strings = psiMethods.get(psiMethod);
        if (strings == null || strings.isEmpty()) {
          strings = new ArrayList<>();
        }
        setupParameterName(location, strings);
        psiMethods.put(psiMethod, strings);
      }
    }
  }

  private static void setupParameterName(Location location, List<String> strings) {
    if (location instanceof PsiMemberParameterizedLocation) {
      final String paramSetName = ((PsiMemberParameterizedLocation)location).getParamSetName();
      if (paramSetName != null) {
        strings.add(TestNGConfigurationProducer.getInvocationNumber(paramSetName));
      }
    }
  }
}
