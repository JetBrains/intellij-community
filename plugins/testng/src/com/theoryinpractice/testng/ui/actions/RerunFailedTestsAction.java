/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.JavaAwareFilter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGRunnableState;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import com.theoryinpractice.testng.model.TestProxy;
import com.theoryinpractice.testng.ui.TestNGResults;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;
    if (myModel == null || myModel.getRoot() == null) return false;
    List<AbstractTestProxy> failed = getFailedTests();
    return !failed.isEmpty();
  }

  @NotNull
  private List<AbstractTestProxy> getFailedTests() {
    List<TestProxy> myAllTests = myModel.getRoot().getAllTests();
    return Filter.DEFECTIVE_LEAF.and(JavaAwareFilter.METHOD(myConsoleProperties.getProject())).select(myAllTests);
  }

  public void actionPerformed(AnActionEvent e) {
    final List<AbstractTestProxy> failed = getFailedTests();

    final DataContext dataContext = e.getDataContext();
    final TestNGConfiguration configuration = myConsoleProperties.getConfiguration();
    boolean isDebug = myConsoleProperties.isDebug();
    try {
      final RunProfile profile = new MyRunProfile(configuration, failed);

      final Executor executor = isDebug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance();
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), profile);
      LOG.assertTrue(runner != null);
      runner.execute(executor, new ExecutionEnvironment(profile, myRunnerSettings, myConfigurationPerRunnerSettings, dataContext));
    }
    catch (ExecutionException e1) {
      LOG.error(e1);
    }
  }

  private static class MyRunProfile implements ModuleRunProfile, RunConfiguration {
    private final TestNGConfiguration myConfiguration;
    private final List<AbstractTestProxy> myFailed;

    public MyRunProfile(final TestNGConfiguration configuration, final List<AbstractTestProxy> failed) {
      myConfiguration = configuration;
      myFailed = failed;
    }

    public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
      return new TestNGRunnableState(env, myConfiguration) {
        protected void fillTestObjects(final Map<PsiClass, Collection<PsiMethod>> classes, final Project project)
          throws CantRunException {
          for (AbstractTestProxy proxy : myFailed) {
            final Location location = proxy.getLocation(project);
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

    public UUID getUUID() {
      return myConfiguration.getUUID();
    }

    public String getName() {
      return ExecutionBundle.message("rerun.failed.tests.action.name");
    }

    public void checkConfiguration() throws RuntimeConfigurationException {}

    @NotNull
    public Module[] getModules() {
      return Module.EMPTY_ARRAY;
    }

    ///////////////////////////////////Delegates
    public void readExternal(final Element element) throws InvalidDataException {
      myConfiguration.readExternal(element);
    }

    public void writeExternal(final Element element) throws WriteExternalException {
      myConfiguration.writeExternal(element);
    }

    public ConfigurationFactory getFactory() {
      return myConfiguration.getFactory();
    }

    public void setName(final String name) {
      myConfiguration.setName(name);
    }

    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return myConfiguration.getConfigurationEditor();
    }

    public Project getProject() {
      return myConfiguration.getProject();
    }

    @NotNull
    public ConfigurationType getType() {
      return myConfiguration.getType();
    }

    public JDOMExternalizable createRunnerSettings(final ConfigurationInfoProvider provider) {
      return myConfiguration.createRunnerSettings(provider);
    }

    public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(final ProgramRunner runner) {
      return myConfiguration.getRunnerSettingsEditor(runner);
    }

    public RunConfiguration clone() {
      return myConfiguration.clone();
    }

    public Object getExtensionSettings(final Class<? extends RunConfigurationExtension> extensionClass) {
      return myConfiguration.getExtensionSettings(extensionClass);
    }

    public void setExtensionSettings(final Class<? extends RunConfigurationExtension> extensionClass, final Object value) {
      myConfiguration.setExtensionSettings(extensionClass, value);
    }
  }
}
