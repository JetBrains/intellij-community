/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.theoryinpractice.testng.TestNGConfiguration;
import com.theoryinpractice.testng.TestNGRunnableState;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import com.theoryinpractice.testng.model.TestProxy;
import com.theoryinpractice.testng.ui.TestNGResults;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RerunFailedTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction");
  private TestNGResults myModel;
  private final TestNGConsoleProperties myConsoleProperties;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;

  public RerunFailedTestsAction(final TestNGConsoleProperties consoleProperties,
                                final RunnerSettings runnerSettings,
                                final ConfigurationPerRunnerSettings configurationSettings) {
    super(ExecutionBundle.message("rerun.failed.tests.action.name"), ExecutionBundle.message("rerun.failed.tests.action.description"),
          IconLoader.getIcon("/runConfigurations/rerunFailedTests.png"));
    myConsoleProperties = consoleProperties;
    myRunnerSettings = runnerSettings;
    myConfigurationPerRunnerSettings = configurationSettings;
  }

  public void setResults(TestNGResults model) {
    myModel = model;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isActive(e));
  }

  private boolean isActive(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return false;
    if (myModel == null || myModel.getRoot() == null) return false;
    List<AbstractTestProxy> failed = getFailedTests();
    return !failed.isEmpty();
  }

  @NotNull
  private List<AbstractTestProxy> getFailedTests() {
    List<TestProxy> myAllTests = myModel.getRoot().getAllTests();
    return Filter.DEFECTIVE_LEAF.and(Filter.METHOD(myConsoleProperties.getProject())).select(myAllTests);
  }

  public void actionPerformed(AnActionEvent e) {
    final List<AbstractTestProxy> failed = getFailedTests();

    final DataContext dataContext = e.getDataContext();
    final TestNGConfiguration configuration = myConsoleProperties.getConfiguration();
    boolean isDebug = myConsoleProperties.getDebugSession() != null;
    final JavaProgramRunner defaultRunner =
      isDebug ? ExecutionRegistry.getInstance().getDebuggerRunner() : ExecutionRegistry.getInstance().getDefaultRunner();
    try {
      final RunProfile profile = new RunProfile() {
        public RunProfileState getState(DataContext context,
                                        RunnerInfo runnerInfo,
                                        RunnerSettings runnerSettings,
                                        ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
          return new TestNGRunnableState(runnerSettings, configurationSettings, configuration) {
            protected boolean fillTestObjects(final Map<PsiClass, Collection<PsiMethod>> classes, final Project project)
              throws CantRunException {
              for (AbstractTestProxy proxy : failed) {
                final Location location = proxy.getLocation(project);
                if (location != null) {
                  final PsiElement element = location.getPsiElement();
                  if (element instanceof PsiMethod) {
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
              return true;
            }
          };
        }

        public String getName() {
          return ExecutionBundle.message("rerun.failed.tests.action.name");
        }

        public void checkConfiguration() throws RuntimeConfigurationException {

        }

        public Module[] getModules() {
          return Module.EMPTY_ARRAY;
        }
      };

      RunStrategy.getInstance().execute(profile, dataContext, defaultRunner, myRunnerSettings, myConfigurationPerRunnerSettings);
    }
    catch (ExecutionException e1) {
      LOG.error(e1);
    }
  }
}
