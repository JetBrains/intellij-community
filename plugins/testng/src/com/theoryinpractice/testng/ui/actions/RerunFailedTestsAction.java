package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.actions.JavaRerunFailedTestsAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.configuration.SearchingForTestsTask;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGRunnableState;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.ServerSocket;
import java.util.*;

public class RerunFailedTestsAction extends JavaRerunFailedTestsAction {

  public RerunFailedTestsAction(@NotNull ComponentContainer componentContainer) {
    super(componentContainer);
  }

  @Override
  public MyRunProfile getRunProfile() {
    final TestNGConfiguration configuration = (TestNGConfiguration)getModel().getProperties().getConfiguration();
    final List<AbstractTestProxy> failedTests = getFailedTests(configuration.getProject());
    return new MyRunProfile(configuration) {
      @NotNull
      public Module[] getModules() {
        return Module.EMPTY_ARRAY;
      }

      public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {

        return new TestNGRunnableState(env, configuration) {
          @Override
          protected SearchingForTestsTask createSearchingForTestsTask(ServerSocket serverSocket,
                                                                      final TestNGConfiguration config, final File tempFile) {
            return new SearchingForTestsTask(serverSocket, config, tempFile, client) {
              @Override
              protected void fillTestObjects(final Map<PsiClass, Collection<PsiMethod>> classes) throws CantRunException {
                final HashMap<PsiClass, Collection<PsiMethod>> fullClassList = ContainerUtil.newHashMap();
                super.fillTestObjects(fullClassList);
                for (final PsiClass aClass : fullClassList.keySet()) {
                  if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
                    @Override
                    public Boolean compute() {
                      return TestNGUtil.hasTest(aClass);
                    }
                  })) {
                    classes.put(aClass, fullClassList.get(aClass));
                  }
                }

                final GlobalSearchScope scope = config.getConfigurationModule().getSearchScope();
                final Project project = config.getProject();
                for (AbstractTestProxy proxy : failedTests) {
                  final Location location = proxy.getLocation(project, scope);
                  if (location != null) {
                    final PsiElement element = location.getPsiElement();
                    if (element instanceof PsiMethod && element.isValid()) {
                      final PsiMethod psiMethod = (PsiMethod)element;
                      PsiClass psiClass = psiMethod.getContainingClass();
                      if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                        final AbstractTestProxy parent = proxy.getParent();
                        final PsiElement elt = parent != null ? parent.getLocation(project, scope).getPsiElement() : null;
                        if (elt instanceof PsiClass) {
                          psiClass = (PsiClass)elt;
                        }
                      }
                      Collection<PsiMethod> psiMethods = classes.get(psiClass);
                      if (psiMethods == null) {
                        psiMethods = new ArrayList<PsiMethod>();
                        classes.put(psiClass, psiMethods);
                      }
                      psiMethods.add(psiMethod);
                    }
                  }
                }
              }
            };
          }
        };
      }
    };
  }

}
