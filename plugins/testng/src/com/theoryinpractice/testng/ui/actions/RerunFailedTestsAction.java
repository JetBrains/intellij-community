/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.theoryinpractice.testng.configuration.SearchingForTestsTask;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGRunnableState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RerunFailedTestsAction extends AbstractRerunFailedTestsAction {
  public RerunFailedTestsAction(JComponent parent) {
    copyFrom(ActionManager.getInstance().getAction("RerunFailedTests"));
    registerCustomShortcutSet(getShortcutSet(), parent);
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
          protected SearchingForTestsTask createSearchingForTestsTask(ServerSocket serverSocket, boolean is15,
                                                                      final TestNGConfiguration config, final File tempFile) {
            return new SearchingForTestsTask(serverSocket, is15, config, tempFile) {
              @Override
              protected void fillTestObjects(final Map<PsiClass, Collection<PsiMethod>> classes) throws CantRunException {
                for (AbstractTestProxy proxy : failedTests) {
                  final Location location = proxy.getLocation(config.getProject());
                  if (location != null) {
                    final PsiElement element = location.getPsiElement();
                    if (element instanceof PsiMethod && element.isValid()) {
                      final PsiMethod psiMethod = (PsiMethod)element;
                      final PsiClass psiClass = psiMethod.getContainingClass();
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
